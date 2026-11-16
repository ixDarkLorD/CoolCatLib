package net.ixdarklord.coolcatcanvas.internal.client.vfx;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.ixdarklord.coolcatcanvas.api.client.vfx.GhostBlocks;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;

// Draws GhostBlocks: the blocks opaque, with the level's camera, into a screen-sized target with its own depth; then
// that picture laid over the world at one opacity, where the world's depth lets it show (shaders core/ghost: GHOST_MODE
// 0 through walls, 1 in the world, 2 always). Seen through walls, each block is drawn a hair larger than itself, so
// where it is in plain sight its ghost is just in front of the real block and leaves it showing; in the world, a hair
// smaller, so it doesn't fight a real block beside it. Over the block, the ghosts are shrunk towards the camera instead:
// they cover exactly the real blocks' pixels, just in front of them, and never spill onto the blocks beside them.
//
// Lit as the world lights them (GhostBlocks.Light.WORLD), a block is built as the game builds blocks in the world
// (ModelBlockRenderer, with the level for its neighbours and light, as a piston's moving block is): each face shaded
// by its side, smooth lighting, and the block and sky light where it stands. What that makes (each quad with its
// lighting) is kept for each place: a ghost drawn every frame is built again only when its block changes, or every
// LIT_MILLIS so it follows changes in the light, and forgotten once it hasn't been drawn for FORGET_MILLIS.
//
// Several groups at once (each its own colour and opacity), all lit like the world and all solid, go into one picture
// with each group's colour and opacity in its vertices, laid over the world in one pass that takes the opacity from the
// picture (GHOST_ALPHA): one clear and one full-screen pass for them all, instead of one of each per group. Anything
// else (a cutout or translucent quad, whose alpha the block pipelines use to cut it out) is drawn a group at a time.
public final class GhostRenderer {
    private static final float GROW = 1.02F;
    private static final float SHRINK = 0.998F;
    // How far towards the camera ghosts over their blocks are drawn, as a share of their distance.
    private static final float TOWARD_CAMERA = 0.997F;
    private static final long LIT_MILLIS = 500, FORGET_MILLIS = 2000;
    private static final Map<Long, LitBlock> LIT = new HashMap<>();
    private static long lastSwept;
    private static final int FULL_BRIGHT = 0xF000F0;
    // Two lights this strong, each way along a diagonal, light any face full: 0.4 + 0.6 * (l0 . n + l1 . n), capped at 1.
    private static final float LIGHT_STRENGTH = 10.0F;
    private static final BlockDisplayContext CONTEXT = BlockDisplayContext.create();
    private static final Map<GhostBlocks.Show, RenderPipeline> COMPOSITES = new EnumMap<>(GhostBlocks.Show.class);
    private static final Map<GhostBlocks.Show, RenderPipeline> ALPHA_COMPOSITES = new EnumMap<>(GhostBlocks.Show.class);
    // Effects' own composites, by shader and how they show; and the uniform block they're given (GhostEffect, in
    // coolcatlib:ghost_effect.glsl): the inverse of the camera's view and projection, the camera, and four vec4s of
    // the effect's own.
    private static final Map<String, RenderPipeline> EFFECT_COMPOSITES = new HashMap<>();
    private static final int EFFECT_UBO_SIZE = 64 + 16 + 4 * 16;
    private static @Nullable GpuBuffer effectUniforms;
    private static final QuadInstance TINTED = new QuadInstance();
    private static @Nullable ModelBlockRenderer worldBlocks;
    private static boolean worldBlocksSmooth;

    private static @Nullable TextureTarget target;
    private static @Nullable BlockModelResolver resolver;
    private static @Nullable GpuBuffer brightLights;
    private static final Map<BlockState, BlockModelRenderState> MODELS = new HashMap<>();

    private GhostRenderer() {}

    public static void clearModels() {
        resolver = null;
        MODELS.clear();
        LIT.clear();
    }

    public static void draw(Collection<GhostBlocks.Placed> blocks, int argb, GhostBlocks.Show show, GhostBlocks.Light light) {
        if (blocks.isEmpty() || (argb >>> 24) == 0) return;
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget main = minecraft.getMainRenderTarget();
        if (main.getDepthTextureView() == null || main.getColorTextureView() == null) return;
        TextureTarget ghosts = target(main);
        drawBlocks(minecraft, ghosts, List.of(blocks), null, size(show), toward(show), light);
        composite(main, ghosts, argb, show, false);
    }

    public static void draw(List<? extends Collection<GhostBlocks.Placed>> groups, int[] argbs, GhostBlocks.Show show, GhostBlocks.Light light) {
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget main = minecraft.getMainRenderTarget();
        if (main.getDepthTextureView() == null || main.getColorTextureView() == null) return;
        boolean any = false;
        for (int i = 0; i < groups.size(); i++) any |= !groups.get(i).isEmpty() && (argbs[i] >>> 24) != 0;
        if (!any) return;
        if (light != GhostBlocks.Light.WORLD || minecraft.level == null || !allSolid(minecraft, minecraft.level, groups, argbs)) {
            for (int i = 0; i < groups.size(); i++) draw(groups.get(i), argbs[i], show, light);
            return;
        }
        TextureTarget ghosts = target(main);
        drawBlocks(minecraft, ghosts, groups, argbs, size(show), toward(show), light);
        composite(main, ghosts, 0xFFFFFFFF, show, true);
    }

    public static void drawEffect(Collection<GhostBlocks.Placed> blocks, GhostBlocks.Show show, GhostBlocks.Light light, Identifier shader,
                                  @Nullable Identifier texture, float[] params) {
        if (blocks.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget main = minecraft.getMainRenderTarget();
        if (main.getDepthTextureView() == null || main.getColorTextureView() == null) return;
        TextureTarget ghosts = target(main);
        float toward = toward(show);
        drawBlocks(minecraft, ghosts, List.of(blocks), null, size(show), toward, light);
        effectComposite(minecraft, main, ghosts, show, shader, texture, params, toward);
    }

    private static float size(GhostBlocks.Show show) {
        return show == GhostBlocks.Show.THROUGH_WALLS ? GROW : show == GhostBlocks.Show.OVER_BLOCK ? 1.0F : SHRINK;
    }

    private static float toward(GhostBlocks.Show show) {
        return show == GhostBlocks.Show.OVER_BLOCK ? TOWARD_CAMERA : 1.0F;
    }

    // Whether every block to be drawn is a model whose quads (lit like the world) are all solid, so the groups can share a
    // picture with their opacity in it.
    private static boolean allSolid(Minecraft minecraft, ClientLevel level, List<? extends Collection<GhostBlocks.Placed>> groups, int[] argbs) {
        long now = Util.getMillis();
        ModelBlockRenderer renderer = worldBlocks(minecraft);
        for (int i = 0; i < groups.size(); i++) {
            if ((argbs[i] >>> 24) == 0) continue;
            for (GhostBlocks.Placed block : groups.get(i)) {
                if (block.state().getRenderShape() != RenderShape.MODEL) return false;
                LitBlock lit = lit(minecraft, renderer, level, block, now);
                for (int q = 0; q < lit.quads.size(); q++) {
                    if (lit.quads.get(q).materialInfo().layer() != ChunkSectionLayer.SOLID) return false;
                }
            }
        }
        return true;
    }

    private static ModelBlockRenderer worldBlocks(Minecraft minecraft) {
        boolean smooth = minecraft.options.ambientOcclusion().get();
        if (worldBlocks == null || worldBlocksSmooth != smooth) {
            worldBlocks = new ModelBlockRenderer(smooth, true, minecraft.getBlockColors());
            worldBlocksSmooth = smooth;
            LIT.clear();
        }
        return worldBlocks;
    }

    // The blocks, opaque, with the camera the level was drawn with. With argbs, each group's colour and opacity go in its
    // vertices (all world-lit and solid, as allSolid checked).
    private static void drawBlocks(Minecraft minecraft, TextureTarget ghosts, List<? extends Collection<GhostBlocks.Placed>> groups, int @Nullable [] argbs,
                                   float size, float toward, GhostBlocks.Light light) {
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(ghosts.getColorTexture(), 0, ghosts.getDepthTexture(), 1.0);
        CameraRenderState camera = minecraft.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState;
        // The level has popped its view from the model-view stack by now: it goes back on for these blocks.
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(camera.viewRotationMatrix);
        RenderSystem.outputColorTextureOverride = ghosts.getColorTextureView();
        RenderSystem.outputDepthTextureOverride = ghosts.getDepthTextureView();
        try {
            if (resolver == null) resolver = new BlockModelResolver(minecraft.getModelManager());
            BlockModelResolver models = resolver;
            if (light == GhostBlocks.Light.BRIGHT) RenderSystem.setShaderLights(brightLights());
            else minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.LEVEL);
            FeatureRenderDispatcher features = minecraft.gameRenderer.getFeatureRenderDispatcher();
            SubmitNodeStorage storage = features.getSubmitNodeStorage();
            ClientLevel level = light == GhostBlocks.Light.WORLD ? minecraft.level : null;
            MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
            PoseStack pose = new PoseStack();
            // Shrunk towards the camera (its origin here): each point drawn where it was on screen, only nearer.
            pose.scale(toward, toward, toward);
            long now = Util.getMillis();
            ModelBlockRenderer renderer = null;
            if (level != null) {
                renderer = worldBlocks(minecraft);
                sweep(now);
            }
            for (int group = 0; group < groups.size(); group++) {
                int argb = argbs == null ? 0xFFFFFFFF : argbs[group];
                if ((argb >>> 24) == 0) continue;
                for (GhostBlocks.Placed block : groups.get(group)) {
                    if (level != null && block.state().getRenderShape() == RenderShape.MODEL) {
                        LitBlock lit = lit(minecraft, renderer, level, block, now);
                        pose.pushPose();
                        pose.translate(block.pos().getX() - camera.pos.x + 0.5, block.pos().getY() - camera.pos.y + 0.5, block.pos().getZ() - camera.pos.z + 0.5);
                        pose.scale(size, size, size);
                        pose.translate(-0.5F + lit.offsetX, -0.5F + lit.offsetY, -0.5F + lit.offsetZ);
                        for (int i = 0; i < lit.quads.size(); i++) {
                            BakedQuad quad = lit.quads.get(i);
                            QuadInstance lighting = lit.lighting.get(i);
                            if (argbs != null) lighting = tinted(lighting, argb);
                            buffers.getBuffer(movingBlock(quad.materialInfo().layer())).putBakedQuad(pose.last(), quad, lighting);
                        }
                        pose.popPose();
                        continue;
                    }
                    BlockModelRenderState model = MODELS.computeIfAbsent(block.state(), state -> {
                        BlockModelRenderState made = new BlockModelRenderState();
                        models.update(made, state, CONTEXT);
                        return made;
                    });
                    if (model.isEmpty()) continue;
                    pose.pushPose();
                    // From the camera, in floats: the block's place less the camera's stays small anywhere in the world.
                    pose.translate(block.pos().getX() - camera.pos.x + 0.5, block.pos().getY() - camera.pos.y + 0.5, block.pos().getZ() - camera.pos.z + 0.5);
                    pose.scale(size, size, size);
                    pose.translate(-0.5F, -0.5F, -0.5F);
                    // (A block entity's model, lit like the world: by the light where it stands.)
                    int lightCoords = level != null ? LevelRenderer.getLightCoords(level, block.pos()) : FULL_BRIGHT;
                    model.submit(pose, storage, lightCoords, OverlayTexture.NO_OVERLAY, 0);
                    pose.popPose();
                }
            }
            features.renderAllFeatures();
            buffers.endBatch();
        } finally {
            minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.LEVEL);
            RenderSystem.outputColorTextureOverride = null;
            RenderSystem.outputDepthTextureOverride = null;
            modelView.popMatrix();
        }
    }

    // A block as the world lights it where it stands: its quads and each one's lighting, built when it's first drawn
    // there, again when it changes, and every LIT_MILLIS.
    private static final class LitBlock {
        private BlockState state;
        private long builtAt;
        private long drawnAt;
        // Where its model sits in its block: a flower's or grass's own small offset, as the world draws it.
        private float offsetX, offsetY, offsetZ;
        private final List<BakedQuad> quads = new ArrayList<>();
        private final List<QuadInstance> lighting = new ArrayList<>();

        private LitBlock(BlockState state) {
            this.state = state;
        }
    }

    private static LitBlock lit(Minecraft minecraft, ModelBlockRenderer renderer, ClientLevel level, GhostBlocks.Placed block, long now) {
        LitBlock lit = LIT.computeIfAbsent(block.pos().asLong(), key -> new LitBlock(block.state()));
        lit.drawnAt = now;
        if (lit.state == block.state() && !lit.quads.isEmpty() && now - lit.builtAt < LIT_MILLIS) return lit;
        lit.state = block.state();
        lit.builtAt = now;
        lit.quads.clear();
        lit.lighting.clear();
        renderer.tesselateBlock((x, y, z, quad, instance) -> {
            lit.offsetX = x;
            lit.offsetY = y;
            lit.offsetZ = z;
            QuadInstance copy = new QuadInstance();
            for (int corner = 0; corner < 4; corner++) {
                copy.setColor(corner, instance.getColor(corner));
                copy.setLightCoords(corner, instance.getLightCoords(corner));
            }
            copy.setOverlayCoords(instance.overlayCoords());
            lit.quads.add(quad);
            lit.lighting.add(copy);
        }, 0.0F, 0.0F, 0.0F, level, block.pos(), block.state(), minecraft.getModelManager().getBlockStateModelSet().get(block.state()),
                block.state().getSeed(block.pos()));
        return lit;
    }

    // The quad's lighting with the group's colour and opacity multiplied in (one instance, filled again for each quad).
    private static QuadInstance tinted(QuadInstance lighting, int argb) {
        for (int corner = 0; corner < 4; corner++) {
            TINTED.setColor(corner, ARGB.multiply(lighting.getColor(corner), argb));
            TINTED.setLightCoords(corner, lighting.getLightCoords(corner));
        }
        TINTED.setOverlayCoords(lighting.overlayCoords());
        return TINTED;
    }

    // Forgets the blocks not drawn for a while (looked at once a second).
    private static void sweep(long now) {
        if (now - lastSwept < 1000) return;
        lastSwept = now;
        LIT.values().removeIf(lit -> now - lit.drawnAt > FORGET_MILLIS);
    }

    private static RenderType movingBlock(ChunkSectionLayer layer) {
        return switch (layer) {
            case SOLID -> RenderTypes.solidMovingBlock();
            case CUTOUT -> RenderTypes.cutoutMovingBlock();
            case TRANSLUCENT -> RenderTypes.translucentMovingBlock();
        };
    }

    private static GpuBufferSlice brightLights() {
        if (brightLights == null) {
            float strength = LIGHT_STRENGTH / (float) Math.sqrt(3.0);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer data = Std140Builder.onStack(stack, Lighting.UBO_SIZE)
                        .putVec3(strength, strength, strength)
                        .putVec3(-strength, -strength, -strength)
                        .get();
                brightLights = RenderSystem.getDevice().createBuffer(() -> "CoolCatLib ghost lights", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, Lighting.UBO_SIZE);
                RenderSystem.getDevice().createCommandEncoder().writeToBuffer(brightLights.slice(), data);
            }
        }
        return brightLights.slice();
    }

    private static RenderPipeline composite(GhostBlocks.Show show, boolean alpha) {
        return (alpha ? ALPHA_COMPOSITES : COMPOSITES).computeIfAbsent(show, mode -> RenderPipeline.builder()
                .withLocation(CoolCatCanvas.rl("pipeline/ghost_" + mode.name().toLowerCase(Locale.ROOT) + (alpha ? "_alpha" : "")))
                .withVertexShader("core/screenquad")
                .withFragmentShader(CoolCatCanvas.rl("core/ghost"))
                .withShaderDefine("GHOST_MODE", mode.ordinal())
                .withShaderDefine("GHOST_ALPHA", alpha ? 1 : 0)
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withSampler("GhostSampler")
                .withSampler("GhostDepthSampler")
                .withSampler("DepthSampler")
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                .build());
    }

    // The ghosts' picture over the world, where the world's depth lets it show.
    private static void composite(RenderTarget main, TextureTarget ghosts, int argb, GhostBlocks.Show show, boolean alpha) {
        Vector4f modulator = new Vector4f((argb >> 16 & 0xFF) / 255.0F, (argb >> 8 & 0xFF) / 255.0F, (argb & 0xFF) / 255.0F, (argb >>> 24) / 255.0F);
        var transforms = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(), modulator, new Vector3f(), new Matrix4f());
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "CoolCatLib ghosts", main.getColorTextureView(), OptionalInt.empty())) {
            pass.setPipeline(composite(show, alpha));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.bindTexture("GhostSampler", ghosts.getColorTextureView(), nearest);
            pass.bindTexture("GhostDepthSampler", ghosts.getDepthTextureView(), nearest);
            pass.bindTexture("DepthSampler", main.getDepthTextureView(), nearest);
            pass.draw(0, 3);
        }
    }

    private static RenderPipeline effectComposite(GhostBlocks.Show show, Identifier shader) {
        return EFFECT_COMPOSITES.computeIfAbsent(shader + "/" + show.name(), key -> RenderPipeline.builder()
                .withLocation(CoolCatCanvas.rl("pipeline/ghost_effect_" + show.name().toLowerCase(Locale.ROOT) + "_"
                        + shader.getNamespace() + "_" + shader.getPath().replace('/', '_')))
                .withVertexShader("core/screenquad")
                .withFragmentShader(shader)
                .withShaderDefine("GHOST_MODE", show.ordinal())
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("GhostEffect", UniformType.UNIFORM_BUFFER)
                .withSampler("GhostSampler")
                .withSampler("GhostDepthSampler")
                .withSampler("DepthSampler")
                .withSampler("EffectSampler")
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                .build());
    }

    // The effect's shader over the world, where the ghosts show, given where each pixel is in the world.
    private static void effectComposite(Minecraft minecraft, RenderTarget main, TextureTarget ghosts, GhostBlocks.Show show, Identifier shader,
                                        @Nullable Identifier texture, float[] params, float toward) {
        CameraRenderState camera = minecraft.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState;
        Matrix4f inverse = new Matrix4f(camera.projectionMatrix).mul(camera.viewRotationMatrix).invert();
        if (effectUniforms == null) {
            effectUniforms = RenderSystem.getDevice().createBuffer(() -> "CoolCatLib ghost effect", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, EFFECT_UBO_SIZE);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder data = Std140Builder.onStack(stack, EFFECT_UBO_SIZE)
                    .putMat4f(inverse)
                    .putVec4((float) camera.pos.x, (float) camera.pos.y, (float) camera.pos.z, toward);
            for (int i = 0; i < 4; i++) {
                data.putVec4(param(params, i * 4), param(params, i * 4 + 1), param(params, i * 4 + 2), param(params, i * 4 + 3));
            }
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(effectUniforms.slice(), data.get());
        }
        var transforms = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f());
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        GpuSampler repeat = RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST);
        // Fetched before the pass opens: the first time, the texture is loaded, which can't happen during a pass.
        GpuTextureView effectTexture = texture != null ? minecraft.getTextureManager().getTexture(texture).getTextureView() : ghosts.getColorTextureView();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "CoolCatLib ghost effect", main.getColorTextureView(), OptionalInt.empty())) {
            pass.setPipeline(effectComposite(show, shader));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setUniform("GhostEffect", effectUniforms.slice());
            pass.bindTexture("GhostSampler", ghosts.getColorTextureView(), nearest);
            pass.bindTexture("GhostDepthSampler", ghosts.getDepthTextureView(), nearest);
            pass.bindTexture("DepthSampler", main.getDepthTextureView(), nearest);
            pass.bindTexture("EffectSampler", effectTexture, texture != null ? repeat : nearest);
            pass.draw(0, 3);
        }
    }

    private static float param(float[] params, int index) {
        return index < params.length ? params[index] : 0.0F;
    }

    private static TextureTarget target(RenderTarget main) {
        if (target == null) {
            target = new TextureTarget("CoolCatLib ghosts", main.width, main.height, true);
        } else if (target.width != main.width || target.height != main.height) {
            target.resize(main.width, main.height);
        }
        return target;
    }
}
