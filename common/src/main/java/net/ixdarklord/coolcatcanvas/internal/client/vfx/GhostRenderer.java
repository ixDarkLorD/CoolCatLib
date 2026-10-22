package net.ixdarklord.coolcatcanvas.internal.client.vfx;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.ixdarklord.coolcatcanvas.api.client.vfx.GhostBlocks;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

// Draws GhostBlocks: the blocks opaque, with the level's camera, into a screen-sized target with its own depth; then
// that picture laid over the world at one opacity, where the world's depth lets it show (shaders core/ghost: GHOST_MODE
// 0 through walls, 1 in the world, 2 always). Seen through walls, each block is drawn a hair larger than itself, so
// where it is in plain sight its ghost is just in front of the real block and leaves it showing; in the world, a hair
// smaller, so it doesn't fight a real block beside it.
public final class GhostRenderer {
    private static final float GROW = 1.02F;
    private static final float SHRINK = 0.998F;
    private static final int FULL_BRIGHT = 0xF000F0;
    // Two lights this strong, each way along a diagonal, light any face full: 0.4 + 0.6 * (l0 . n + l1 . n), capped at 1.
    private static final float LIGHT_STRENGTH = 10.0F;
    private static final BlockDisplayContext CONTEXT = BlockDisplayContext.create();
    private static final Map<GhostBlocks.Show, RenderPipeline> COMPOSITES = new EnumMap<>(GhostBlocks.Show.class);

    private static @Nullable TextureTarget target;
    private static @Nullable BlockModelResolver resolver;
    private static @Nullable GpuBuffer brightLights;
    private static final Map<BlockState, BlockModelRenderState> MODELS = new HashMap<>();

    private GhostRenderer() {}

    public static void clearModels() {
        resolver = null;
        MODELS.clear();
    }

    public static void draw(Collection<GhostBlocks.Placed> blocks, int argb, GhostBlocks.Show show, boolean shaded) {
        if (blocks.isEmpty() || (argb >>> 24) == 0) return;
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget main = minecraft.gameRenderer.mainRenderTarget();
        if (main.getDepthTextureView() == null || main.getColorTextureView() == null) return;
        TextureTarget ghosts = target(main);
        float size = show == GhostBlocks.Show.THROUGH_WALLS ? GROW : SHRINK;
        drawBlocks(minecraft, ghosts, blocks, size, shaded);
        composite(main, ghosts, argb, show);
    }

    // The blocks, opaque, with the camera the level was drawn with.
    private static void drawBlocks(Minecraft minecraft, TextureTarget ghosts, Collection<GhostBlocks.Placed> blocks, float size, boolean shaded) {
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(ghosts.getColorTexture(), new org.joml.Vector4f(0), ghosts.getDepthTexture(), 0.0);
        CameraRenderState camera = minecraft.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        // The level has popped its view from the model-view stack by now: it goes back on for these blocks.
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(camera.viewRotationMatrix);
        try {
            if (resolver == null) resolver = new BlockModelResolver(minecraft.getModelManager());
            BlockModelResolver models = resolver;
            if (shaded) minecraft.gameRenderer.lighting().setupFor(Lighting.Entry.LEVEL);
            else RenderSystem.setShaderLights(brightLights());
            FeatureRenderDispatcher features = minecraft.gameRenderer.featureRenderDispatcher();
            SubmitNodeStorage storage = new SubmitNodeStorage();
            PoseStack pose = new PoseStack();
            for (GhostBlocks.Placed block : blocks) {
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
                model.submit(pose, storage, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
                pose.popPose();
            }
            // Drawn into the ghosts' own picture.
            try (FeatureRenderDispatcher.PreparedFrame frame = features.prepareFrame(storage);
                 RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "CoolCatLib ghost blocks",
                         ghosts.getColorTextureView(), java.util.Optional.empty(), ghosts.getDepthTextureView(), java.util.OptionalDouble.empty())) {
                RenderSystem.bindDefaultUniforms(pass);
                FeatureRenderDispatcher.renderAllFeatures(pass, frame);
            }
        } finally {
            minecraft.gameRenderer.lighting().setupFor(Lighting.Entry.LEVEL);
            modelView.popMatrix();
        }
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

    private static RenderPipeline composite(GhostBlocks.Show show) {
        return COMPOSITES.computeIfAbsent(show, mode -> RenderPipeline.builder()
                .withLocation(CoolCatCanvas.rl("pipeline/ghost_" + mode.name()))
                .withVertexShader("core/screenquad")
                .withFragmentShader(CoolCatCanvas.rl("core/ghost"))
                .withShaderDefine("GHOST_MODE", mode.ordinal())
                .withBindGroupLayout(com.mojang.renderpearl.api.pipeline.BindGroupLayout.builder().withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER).build())
                .withBindGroupLayout(com.mojang.renderpearl.api.pipeline.BindGroupLayout.builder().withUniform("GhostSampler", com.mojang.renderpearl.api.pipeline.UniformType.COMBINED_IMAGE_SAMPLER).build())
                .withBindGroupLayout(com.mojang.renderpearl.api.pipeline.BindGroupLayout.builder().withUniform("GhostDepthSampler", com.mojang.renderpearl.api.pipeline.UniformType.COMBINED_IMAGE_SAMPLER).build())
                .withBindGroupLayout(com.mojang.renderpearl.api.pipeline.BindGroupLayout.builder().withUniform("DepthSampler", com.mojang.renderpearl.api.pipeline.UniformType.COMBINED_IMAGE_SAMPLER).build())
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withPrimitiveTopology(com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
                .build());
    }

    // The ghosts' picture over the world, where the world's depth lets it show.
    private static void composite(RenderTarget main, TextureTarget ghosts, int argb, GhostBlocks.Show show) {
        Vector4f modulator = new Vector4f((argb >> 16 & 0xFF) / 255.0F, (argb >> 8 & 0xFF) / 255.0F, (argb & 0xFF) / 255.0F, (argb >>> 24) / 255.0F);
        var transforms = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(), modulator, new Vector3f(), new Matrix4f());
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "CoolCatLib ghosts", main.getColorTextureView(), java.util.Optional.empty())) {
            pass.setPipeline(com.mojang.blaze3d.systems.RenderSystem.getCompiledPipeline(composite(show)));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setUniform("GhostSampler", ghosts.getColorTextureView(), nearest);
            pass.setUniform("GhostDepthSampler", ghosts.getDepthTextureView(), nearest);
            pass.setUniform("DepthSampler", main.getDepthTextureView(), nearest);
            pass.draw(3, 1, 0, 0);
        }
    }

    private static TextureTarget target(RenderTarget main) {
        if (target == null) {
            target = new TextureTarget("CoolCatLib ghosts", main.width, main.height, com.mojang.renderpearl.api.GpuFormat.RGBA8_UNORM, com.mojang.renderpearl.api.GpuFormat.D32_FLOAT);
        } else if (target.width != main.width || target.height != main.height) {
            target.resize(main.width, main.height);
        }
        return target;
    }
}
