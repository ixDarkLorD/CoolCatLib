package net.ixdarklord.coolcatcanvas.internal.client.vfx;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.ixdarklord.coolcatcanvas.api.client.vfx.GhostBlocks;
import net.ixdarklord.coolcatcanvas.internal.client.render.CanvasProgram;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.ixdarklord.coolcatcore.internal.platform.ClientServices;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Draws GhostBlocks: the blocks opaque, with the level's camera, into a screen-sized target with its own depth; then
// that picture laid over the world at one opacity, where the world's depth lets it show (shader core/ghost: GHOST_MODE
// 0 through walls, 1 in the world, 2 always, 3 over their blocks). Seen through walls, each block is drawn a hair larger
// than itself, so where it is in plain sight its ghost is just in front of the real block and leaves it showing; in the
// world, a hair smaller, so it doesn't fight a real block beside it. Over the block, the ghosts are shrunk towards the
// camera instead: they cover exactly the real blocks' pixels, just in front of them, and never spill onto the blocks
// beside them. The world's depth is copied first, as the main target can't be read while it is drawn to.
//
// Lit as the world lights them (GhostBlocks.Light.WORLD), a block is built as the loader builds blocks in chunks (the
// model renderer with the level for its neighbours and light, as a piston's moving block is: ClientServices
// tesselateWorldBlock): each face shaded by its side, smooth lighting, and the block and sky light where it stands, in
// the chunk layers it is drawn in. What that makes (each vertex with its lighting, the model's offset in its block, a
// flower's or grass's, included) is kept for each place: a ghost drawn every frame is built again only when its block
// changes, or every LIT_MILLIS so it follows changes in the light, and forgotten once it hasn't been drawn for
// FORGET_MILLIS. It is drawn back with the chunk layers' own render types, which shade nothing more.
//
// Several groups at once (each its own colour and opacity), all lit like the world and all solid, go into one picture
// with each group's colour and opacity in its vertices, laid over the world in one pass that takes the opacity from the
// picture (GHOST_ALPHA): one clear and one full-screen pass for them all, instead of one of each per group. Anything
// else (a cutout or translucent quad, whose alpha cuts it out) is drawn a group at a time.
//
// drawEffect lays a mod's own fragment shader over the world in place of core/ghost, given each pixel's place in the
// world (coolcatcanvas:ghost_effect.glsl).
public final class GhostRenderer {
    private static final float GROW = 1.02F;
    private static final float SHRINK = 0.998F;
    // How far towards the camera ghosts over their blocks are drawn, as a share of their distance.
    private static final float TOWARD_CAMERA = 0.997F;
    private static final long LIT_MILLIS = 500, FORGET_MILLIS = 2000;
    private static final int FULL_BRIGHT = 0xF000F0;
    // Two lights this strong, each way along a diagonal, light any face full: 0.4 + 0.6 * (l0 . n + l1 . n), capped at 1.
    private static final float LIGHT_STRENGTH = 10.0F;
    private static final ResourceLocation VERTEX_SHADER = new ResourceLocation("minecraft", "shaders/program/sobel.vsh");
    private static final ResourceLocation FRAGMENT_SHADER = CoolCatCanvas.rl("shaders/core/ghost.fsh");
    // Programs by how they show (and, for core/ghost, whether the opacity is in the picture); effects' by shader too.
    private static final Map<String, CanvasProgram> PROGRAMS = new HashMap<>();
    private static final Set<String> FAILED = new HashSet<>();

    private static final Map<Long, LitBlock> LIT = new HashMap<>();
    private static final Capture CAPTURE = new Capture();
    private static final RandomSource RANDOM = RandomSource.create();
    private static long lastSwept;
    private static boolean litSmooth;

    private static @Nullable TextureTarget target;
    private static @Nullable TextureTarget worldDepth;

    private GhostRenderer() {}

    public static void clearModels() {
        LIT.clear();
    }

    // The shaders were reloaded (or the game is closing): the programs are compiled again when next needed.
    public static void close() {
        PROGRAMS.values().forEach(CanvasProgram::close);
        PROGRAMS.clear();
        FAILED.clear();
    }

    public static void draw(Collection<GhostBlocks.Placed> blocks, int argb, GhostBlocks.Show show, GhostBlocks.Light light) {
        if (blocks.isEmpty() || (argb >>> 24) == 0) return;
        RenderSystem.assertOnRenderThread();
        CanvasProgram program = program(show, false);
        if (program == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget main = minecraft.getMainRenderTarget();
        TextureTarget ghosts = target(main);
        drawBlocks(minecraft, ghosts, List.of(blocks), null, size(show), toward(show), light);
        composite(main, ghosts, program, argb);
    }

    public static void draw(List<? extends Collection<GhostBlocks.Placed>> groups, int[] argbs, GhostBlocks.Show show, GhostBlocks.Light light) {
        RenderSystem.assertOnRenderThread();
        boolean any = false;
        for (int i = 0; i < groups.size(); i++) any |= !groups.get(i).isEmpty() && (argbs[i] >>> 24) != 0;
        if (!any) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (light != GhostBlocks.Light.WORLD || minecraft.level == null || !allSolid(minecraft.level, groups, argbs)) {
            for (int i = 0; i < groups.size(); i++) draw(groups.get(i), argbs[i], show, light);
            return;
        }
        CanvasProgram program = program(show, true);
        if (program == null) return;
        RenderTarget main = minecraft.getMainRenderTarget();
        TextureTarget ghosts = target(main);
        drawBlocks(minecraft, ghosts, groups, argbs, size(show), toward(show), light);
        composite(main, ghosts, program, 0xFFFFFFFF);
    }

    public static void drawEffect(Collection<GhostBlocks.Placed> blocks, GhostBlocks.Show show, GhostBlocks.Light light, ResourceLocation shader,
                                  @Nullable ResourceLocation texture, float[] params) {
        if (blocks.isEmpty()) return;
        RenderSystem.assertOnRenderThread();
        CanvasProgram program = effectProgram(show, shader);
        if (program == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget main = minecraft.getMainRenderTarget();
        TextureTarget ghosts = target(main);
        float toward = toward(show);
        drawBlocks(minecraft, ghosts, List.of(blocks), null, size(show), toward, light);
        effectComposite(minecraft, main, ghosts, program, texture, params, toward);
    }

    private static float size(GhostBlocks.Show show) {
        return show == GhostBlocks.Show.THROUGH_WALLS ? GROW : show == GhostBlocks.Show.OVER_BLOCK ? 1.0F : SHRINK;
    }

    private static float toward(GhostBlocks.Show show) {
        return show == GhostBlocks.Show.OVER_BLOCK ? TOWARD_CAMERA : 1.0F;
    }

    // Whether every block to be drawn is a model built (lit like the world) only in the solid layer, so the groups can
    // share a picture with their opacity in it.
    private static boolean allSolid(ClientLevel level, List<? extends Collection<GhostBlocks.Placed>> groups, int[] argbs) {
        long now = Util.getMillis();
        checkSmooth();
        for (int i = 0; i < groups.size(); i++) {
            if ((argbs[i] >>> 24) == 0) continue;
            for (GhostBlocks.Placed block : groups.get(i)) {
                if (block.state().getRenderShape() != RenderShape.MODEL) return false;
                LitBlock lit = lit(level, block, now);
                for (Map.Entry<RenderType, IntArrayList> layer : lit.layers.entrySet()) {
                    if (!layer.getValue().isEmpty() && layer.getKey() != RenderType.solid()) return false;
                }
            }
        }
        return true;
    }

    // Smooth lighting turned on or off: the blocks built are lit the old way.
    private static void checkSmooth() {
        boolean smooth = Minecraft.useAmbientOcclusion();
        if (smooth != litSmooth) {
            litSmooth = smooth;
            LIT.clear();
        }
    }

    // The blocks, opaque, with the camera the level was drawn with. With argbs, each group's colour and opacity go in its
    // vertices (all world-lit and solid, as allSolid checked).
    private static void drawBlocks(Minecraft minecraft, TextureTarget ghosts, List<? extends Collection<GhostBlocks.Placed>> groups, int @Nullable [] argbs,
                                   float size, float toward, GhostBlocks.Light light) {
        ghosts.clear(Minecraft.ON_OSX);
        ghosts.bindWrite(true);
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 eye = camera.getPosition();
        // The level has popped its view from the model-view stack by now: it goes back on for these blocks.
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
        modelView.mulPose(Axis.YP.rotationDegrees(camera.getYRot() + 180.0F));
        RenderSystem.applyModelViewMatrix();
        try {
            if (light == GhostBlocks.Light.BRIGHT) {
                float strength = LIGHT_STRENGTH / (float) Math.sqrt(3.0);
                RenderSystem.setShaderLights(new Vector3f(strength, strength, strength), new Vector3f(-strength, -strength, -strength));
            } else {
                Lighting.setupLevel(modelView.last().pose());
            }
            ClientLevel level = light == GhostBlocks.Light.WORLD ? minecraft.level : null;
            MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
            PoseStack pose = new PoseStack();
            // Shrunk towards the camera (its origin here): each point drawn where it was on screen, only nearer.
            pose.scale(toward, toward, toward);
            long now = Util.getMillis();
            if (level != null) {
                checkSmooth();
                sweep(now);
            }
            for (int group = 0; group < groups.size(); group++) {
                int argb = argbs == null ? 0xFFFFFFFF : argbs[group];
                if ((argb >>> 24) == 0) continue;
                for (GhostBlocks.Placed block : groups.get(group)) {
                    pose.pushPose();
                    // From the camera, in floats: the block's place less the camera's stays small anywhere in the world.
                    pose.translate(block.pos().getX() - eye.x + 0.5, block.pos().getY() - eye.y + 0.5, block.pos().getZ() - eye.z + 0.5);
                    pose.scale(size, size, size);
                    pose.translate(-0.5F, -0.5F, -0.5F);
                    if (level != null && block.state().getRenderShape() == RenderShape.MODEL) {
                        draw(lit(level, block, now), pose, buffers, argbs == null ? -1 : argb);
                    } else {
                        // (A block entity's model, lit like the world: by the light where it stands.)
                        int lightCoords = level != null ? LevelRenderer.getLightColor(level, block.pos()) : FULL_BRIGHT;
                        minecraft.getBlockRenderer().renderSingleBlock(block.state(), pose, buffers, lightCoords, OverlayTexture.NO_OVERLAY);
                    }
                    pose.popPose();
                }
            }
            buffers.endBatch();
        } finally {
            Lighting.setupLevel(modelView.last().pose());
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    // A block as the world lights it where it stands: its vertices in each chunk layer, built when it's first drawn there,
    // again when it changes, and every LIT_MILLIS.
    private static final class LitBlock {
        private BlockState state;
        private boolean built;
        private long builtAt;
        private long drawnAt;
        // By layer: each vertex as Capture.STRIDE ints, its place in the block (with the model's own offset in it, a
        // flower's or grass's, as the world draws it), colour (with the face's shade and smooth lighting), texture,
        // light and normal.
        private final Map<RenderType, IntArrayList> layers = new LinkedHashMap<>();

        private LitBlock(BlockState state) {
            this.state = state;
        }
    }

    private static LitBlock lit(ClientLevel level, GhostBlocks.Placed block, long now) {
        LitBlock lit = LIT.computeIfAbsent(block.pos().asLong(), key -> new LitBlock(block.state()));
        lit.drawnAt = now;
        if (lit.state == block.state() && lit.built && now - lit.builtAt < LIT_MILLIS) return lit;
        lit.state = block.state();
        lit.built = true;
        lit.builtAt = now;
        lit.layers.values().forEach(IntArrayList::clear);
        ClientServices.get().tesselateWorldBlock(level, block.state(), block.pos(), new PoseStack(),
                layer -> CAPTURE.into(lit.layers.computeIfAbsent(layer, key -> new IntArrayList())), RANDOM);
        return lit;
    }

    private static final Vector4f POSITION = new Vector4f();
    private static final Vector3f NORMAL = new Vector3f();

    // A built block drawn back where the pose puts it, in its layers' render types; with a group's colour and opacity
    // multiplied in, unless tint is -1.
    private static void draw(LitBlock lit, PoseStack pose, MultiBufferSource buffers, int tint) {
        Matrix4f matrix = pose.last().pose();
        Matrix3f normals = pose.last().normal();
        float tintA = (tint >>> 24) / 255.0F, tintR = (tint >> 16 & 0xFF) / 255.0F, tintG = (tint >> 8 & 0xFF) / 255.0F, tintB = (tint & 0xFF) / 255.0F;
        for (Map.Entry<RenderType, IntArrayList> layer : lit.layers.entrySet()) {
            IntArrayList data = layer.getValue();
            if (data.isEmpty()) continue;
            VertexConsumer consumer = buffers.getBuffer(ghostLayer(layer.getKey()));
            int[] vertices = data.elements();
            for (int i = 0; i + Capture.STRIDE <= data.size(); i += Capture.STRIDE) {
                POSITION.set(Float.intBitsToFloat(vertices[i]), Float.intBitsToFloat(vertices[i + 1]), Float.intBitsToFloat(vertices[i + 2]), 1.0F).mul(matrix);
                NORMAL.set(Float.intBitsToFloat(vertices[i + 7]), Float.intBitsToFloat(vertices[i + 8]), Float.intBitsToFloat(vertices[i + 9])).mul(normals);
                int color = vertices[i + 3];
                consumer.vertex(POSITION.x(), POSITION.y(), POSITION.z(),
                        (color >> 16 & 0xFF) / 255.0F * tintR, (color >> 8 & 0xFF) / 255.0F * tintG, (color & 0xFF) / 255.0F * tintB, (color >>> 24) / 255.0F * tintA,
                        Float.intBitsToFloat(vertices[i + 4]), Float.intBitsToFloat(vertices[i + 5]), OverlayTexture.NO_OVERLAY, vertices[i + 6],
                        NORMAL.x(), NORMAL.y(), NORMAL.z());
            }
        }
    }

    // The render type a chunk layer is drawn back with: its own (as a piston's moving block is), but the layers drawn to
    // another target under Fabulous graphics (translucent, tripwire) go into the ghosts' picture cut out instead.
    private static RenderType ghostLayer(RenderType layer) {
        if (layer == RenderType.solid() || layer == RenderType.cutout() || layer == RenderType.cutoutMipped()) return layer;
        if (layer == RenderType.translucent() && !Minecraft.useShaderTransparency()) return RenderType.translucentMovingBlock();
        return RenderType.cutout();
    }

    // Forgets the blocks not drawn for a while (looked at once a second).
    private static void sweep(long now) {
        if (now - lastSwept < 1000) return;
        lastSwept = now;
        LIT.values().removeIf(lit -> now - lit.drawnAt > FORGET_MILLIS);
    }

    // The ghosts' picture over the world, where the world's depth lets it show.
    private static void composite(RenderTarget main, TextureTarget ghosts, CanvasProgram program, int argb) {
        TextureTarget depth = worldDepth(main);
        depth.copyDepthFrom(main);
        main.bindWrite(true);
        beginFullScreen();
        program.use();
        bind(0, ghosts.getColorTextureId());
        bind(1, ghosts.getDepthTextureId());
        bind(2, depth.getDepthTextureId());
        program.setInt("GhostSampler", 0);
        program.setInt("GhostDepthSampler", 1);
        program.setInt("DepthSampler", 2);
        program.setVec4("ColorModulator", (argb >> 16 & 0xFF) / 255.0F, (argb >> 8 & 0xFF) / 255.0F, (argb & 0xFF) / 255.0F, (argb >>> 24) / 255.0F);
        fullScreen(main, program);
        endFullScreen(2);
    }

    // The effect's shader over the world, where the ghosts show, given where each pixel is in the world.
    private static void effectComposite(Minecraft minecraft, RenderTarget main, TextureTarget ghosts, CanvasProgram program,
                                        @Nullable ResourceLocation texture, float[] params, float toward) {
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Matrix4f view = new Matrix4f().rotation(Axis.XP.rotationDegrees(camera.getXRot())).rotate(Axis.YP.rotationDegrees(camera.getYRot() + 180.0F));
        Matrix4f inverse = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(view).invert();
        Vec3 eye = camera.getPosition();
        // Fetched before the textures are bound: the first time, the texture is loaded, which binds it.
        int effectTexture = texture != null ? minecraft.getTextureManager().getTexture(texture).getId() : ghosts.getColorTextureId();
        TextureTarget depth = worldDepth(main);
        depth.copyDepthFrom(main);
        main.bindWrite(true);
        beginFullScreen();
        program.use();
        bind(0, ghosts.getColorTextureId());
        bind(1, ghosts.getDepthTextureId());
        bind(2, depth.getDepthTextureId());
        bind(3, effectTexture);
        if (texture != null) {
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        }
        program.setInt("GhostSampler", 0);
        program.setInt("GhostDepthSampler", 1);
        program.setInt("DepthSampler", 2);
        program.setInt("EffectSampler", 3);
        // White, as 26.1.2 gives it: for an effect that declares ColorModulator itself, as shaders there import it.
        program.setVec4("ColorModulator", 1.0F, 1.0F, 1.0F, 1.0F);
        program.setMat4("GhostInverseViewProjection", inverse);
        program.setVec4("GhostCamera", (float) eye.x, (float) eye.y, (float) eye.z, toward);
        for (int i = 0; i < 4; i++) {
            program.setVec4("GhostParams" + i, param(params, i * 4), param(params, i * 4 + 1), param(params, i * 4 + 2), param(params, i * 4 + 3));
        }
        fullScreen(main, program);
        endFullScreen(3);
    }

    private static float param(float[] params, int index) {
        return index < params.length ? params[index] : 0.0F;
    }

    private static void bind(int unit, int texture) {
        RenderSystem.activeTexture(GL13_TEXTURE0 + unit);
        RenderSystem.bindTexture(texture);
    }

    private static final int GL13_TEXTURE0 = 33984;

    private static void beginFullScreen() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
    }

    private static void fullScreen(RenderTarget main, CanvasProgram program) {
        program.setMat4("ProjMat", new Matrix4f().setOrtho(0.0F, main.width, 0.0F, main.height, 0.1F, 1000.0F));
        program.setVec2("OutSize", main.width, main.height);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        builder.vertex(0.0, 0.0, 500.0).endVertex();
        builder.vertex(main.width, 0.0, 500.0).endVertex();
        builder.vertex(main.width, main.height, 500.0).endVertex();
        builder.vertex(0.0, main.height, 500.0).endVertex();
        BufferUploader.draw(builder.end());
    }

    private static void endFullScreen(int lastUnit) {
        for (int unit = lastUnit; unit >= 0; unit--) {
            RenderSystem.activeTexture(GL13_TEXTURE0 + unit);
            RenderSystem.bindTexture(0);
        }
        CanvasProgram.unuse();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static @Nullable CanvasProgram program(GhostBlocks.Show show, boolean alpha) {
        return compiled("ghost/" + show.name() + (alpha ? "/alpha" : ""), FRAGMENT_SHADER,
                List.of("GHOST_MODE " + show.ordinal(), "GHOST_ALPHA " + (alpha ? 1 : 0)));
    }

    private static @Nullable CanvasProgram effectProgram(GhostBlocks.Show show, ResourceLocation shader) {
        return compiled("effect/" + shader + "/" + show.name(), new ResourceLocation(shader.getNamespace(), "shaders/" + shader.getPath() + ".fsh"),
                List.of("GHOST_MODE " + show.ordinal()));
    }

    private static @Nullable CanvasProgram compiled(String key, ResourceLocation fragment, List<String> defines) {
        CanvasProgram program = PROGRAMS.get(key);
        if (program != null || FAILED.contains(key)) return program;
        try {
            program = CanvasProgram.compile(Minecraft.getInstance().getResourceManager(), "ghost blocks " + key, VERTEX_SHADER, fragment, defines);
        } catch (IOException e) {
            CoolCatCanvas.LOGGER.error("Couldn't compile the ghost blocks shader {}", fragment, e);
            FAILED.add(key);
            return null;
        }
        PROGRAMS.put(key, program);
        return program;
    }

    private static TextureTarget target(RenderTarget main) {
        if (target == null) {
            target = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
            target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        } else if (target.width != main.width || target.height != main.height) {
            target.resize(main.width, main.height, Minecraft.ON_OSX);
        }
        return target;
    }

    private static TextureTarget worldDepth(RenderTarget main) {
        if (worldDepth == null) {
            worldDepth = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        } else if (worldDepth.width != main.width || worldDepth.height != main.height) {
            worldDepth.resize(main.width, main.height, Minecraft.ON_OSX);
        }
        return worldDepth;
    }

    // Takes the vertices a block is built into: STRIDE ints each (x, y, z, ARGB colour, u, v, packed light, normal x,
    // y, z), floats as their bits.
    private static final class Capture implements VertexConsumer {
        static final int STRIDE = 10;
        private IntArrayList into = new IntArrayList();
        private float x, y, z, u, v, normalX, normalY, normalZ;
        private int color = -1, light;

        private Capture into(IntArrayList into) {
            this.into = into;
            return this;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            this.x = (float) x;
            this.y = (float) y;
            this.z = (float) z;
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            this.color = (alpha & 0xFF) << 24 | (red & 0xFF) << 16 | (green & 0xFF) << 8 | blue & 0xFF;
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            this.u = u;
            this.v = v;
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            this.light = u & 0xFFFF | (v & 0xFFFF) << 16;
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            this.normalX = x;
            this.normalY = y;
            this.normalZ = z;
            return this;
        }

        @Override
        public void endVertex() {
            this.into.add(Float.floatToRawIntBits(this.x));
            this.into.add(Float.floatToRawIntBits(this.y));
            this.into.add(Float.floatToRawIntBits(this.z));
            this.into.add(this.color);
            this.into.add(Float.floatToRawIntBits(this.u));
            this.into.add(Float.floatToRawIntBits(this.v));
            this.into.add(this.light);
            this.into.add(Float.floatToRawIntBits(this.normalX));
            this.into.add(Float.floatToRawIntBits(this.normalY));
            this.into.add(Float.floatToRawIntBits(this.normalZ));
            this.color = -1;
        }

        @Override
        public void vertex(float x, float y, float z, float red, float green, float blue, float alpha, float u, float v, int overlay, int light,
                           float normalX, float normalY, float normalZ) {
            this.vertex(x, y, z);
            this.color((int) (red * 255.0F), (int) (green * 255.0F), (int) (blue * 255.0F), (int) (alpha * 255.0F));
            this.uv(u, v);
            this.uv2(light & 0xFFFF, light >> 16 & 0xFFFF);
            this.normal(normalX, normalY, normalZ);
            this.endVertex();
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
        }

        @Override
        public void unsetDefaultColor() {
        }
    }
}
