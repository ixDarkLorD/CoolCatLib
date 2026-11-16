package net.ixdarklord.coolcatcanvas.internal.client.vfx;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.ixdarklord.coolcatcanvas.api.client.vfx.GhostBlocks;
import net.ixdarklord.coolcatcanvas.internal.client.effect.ShaderProgram;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Draws GhostBlocks: the blocks opaque, with the level's camera, into a screen-sized target with its own depth; then
// that picture laid over the world, where the world's depth lets it show (shader core/ghost: GHOST_MODE 0 through
// walls, 1 in the world, 2 always, 3 over their own blocks). Seen through walls, each block is drawn a hair larger than
// itself, so where it is in plain sight its ghost is just in front of the real block and leaves it showing; in the
// world, a hair smaller, so it doesn't fight a real block beside it. Over the block, the ghosts are shrunk towards the
// camera instead: they cover exactly the real blocks' pixels, just in front of them, and never spill onto the blocks
// beside them. The world's depth is copied first, as the main target can't be read while it is drawn to.
//
// Lit as the world lights them (GhostBlocks.Light.WORLD), a block is built as the game builds blocks in the world
// (ModelBlockRenderer.tesselateBlock, with the level for its neighbours, light and tint, as a piston's moving block
// is): each face shaded by its side, smooth lighting, the block and sky light where it stands, and a plant's offset in
// its block. What that makes (each vertex with its colour and light) is kept for each place and drawn with the chunk
// shaders, as the real block is: a ghost drawn every frame is built again only when its block changes, or every
// LIT_MILLIS so it follows changes in the light, and forgotten once it hasn't been drawn for FORGET_MILLIS. (A
// translucent block, whose chunk pipeline draws to another target with Fabulous graphics, goes through the entity
// translucent sheet instead, with lights that leave its colours as they are.)
//
// Several groups at once (each its own colour and opacity), all lit like the world and all solid, go into one picture
// with each group's colour and opacity in its vertices, laid over the world in one pass that takes the opacity from the
// picture (GHOST_ALPHA): one clear and one full-screen pass for them all, instead of one of each per group. Anything
// else (a cutout or translucent quad, whose alpha cuts it out) is drawn a group at a time.
//
// GhostBlocks.drawEffect lays a mod's own fragment shader over the world in place of the flat colour, compiled with
// CoolCatLib's ShaderProgram (coolcatcanvas:ghost_effect.glsl gives it plain uniforms on this version).
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
    // A normal along that diagonal: lit full by those lights whatever way its face really faces (its shade is already
    // in its colour).
    private static final float DIAGONAL = (float) (1.0 / Math.sqrt(3.0));
    private static final ResourceLocation VERTEX_SHADER = CoolCatCanvas.rl("post/screenquad");
    private static final ResourceLocation FRAGMENT_SHADER = CoolCatCanvas.rl("core/ghost");
    // Programs by GHOST_MODE and GHOST_ALPHA (mode * 2 + alpha); effects' own by shader and mode.
    private static final Map<Integer, ShaderProgram> PROGRAMS = new HashMap<>();
    private static final Map<String, ShaderProgram> EFFECTS = new HashMap<>();
    private static final Set<String> FAILED = new HashSet<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static final Matrix4f ORTHO = new Matrix4f();
    private static final Matrix4f INVERSE = new Matrix4f();

    private static @Nullable TextureTarget target;
    private static @Nullable TextureTarget worldDepth;

    private GhostRenderer() {}

    public static void clearModels() {
        LIT.clear();
    }

    // The shaders were reloaded (or the game is closing): the programs are compiled again when next needed.
    public static void close() {
        PROGRAMS.values().forEach(ShaderProgram::close);
        PROGRAMS.clear();
        EFFECTS.values().forEach(ShaderProgram::close);
        EFFECTS.clear();
        FAILED.clear();
        LIT.clear();
    }

    public static void draw(Collection<GhostBlocks.Placed> blocks, int argb, GhostBlocks.Show show, GhostBlocks.Light light) {
        if (blocks.isEmpty() || (argb >>> 24) == 0) return;
        RenderSystem.assertOnRenderThread();
        ShaderProgram program = program(show, false);
        if (program == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget main = minecraft.getMainRenderTarget();
        TextureTarget ghosts = target(main);
        drawBlocks(minecraft, ghosts, List.of(blocks), null, size(show), toward(show), light);
        composite(main, ghosts, program, argb);
    }

    public static void draw(List<? extends Collection<GhostBlocks.Placed>> groups, int[] argbs, GhostBlocks.Show show, GhostBlocks.Light light) {
        RenderSystem.assertOnRenderThread();
        Minecraft minecraft = Minecraft.getInstance();
        boolean any = false;
        for (int i = 0; i < groups.size(); i++) any |= !groups.get(i).isEmpty() && (argbs[i] >>> 24) != 0;
        if (!any) return;
        if (light != GhostBlocks.Light.WORLD || minecraft.level == null || !allSolid(minecraft, minecraft.level, groups, argbs)) {
            for (int i = 0; i < groups.size(); i++) draw(groups.get(i), argbs[i], show, light);
            return;
        }
        ShaderProgram program = program(show, true);
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
        ShaderProgram program = effect(show, shader);
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

    // Whether every block to be drawn is a model drawn solid, so the groups can share a picture with their opacity in it.
    private static boolean allSolid(Minecraft minecraft, ClientLevel level, List<? extends Collection<GhostBlocks.Placed>> groups, int[] argbs) {
        long now = Util.getMillis();
        for (int i = 0; i < groups.size(); i++) {
            if ((argbs[i] >>> 24) == 0) continue;
            for (GhostBlocks.Placed block : groups.get(i)) {
                if (block.state().getRenderShape() != RenderShape.MODEL) return false;
                if (lit(minecraft, level, block, now).type != RenderType.solid()) return false;
            }
        }
        return true;
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
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(new Matrix4f().rotation(camera.rotation().conjugate(new Quaternionf())));
        RenderSystem.applyModelViewMatrix();
        try {
            ClientLevel level = light == GhostBlocks.Light.WORLD ? minecraft.level : null;
            MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
            PoseStack pose = new PoseStack();
            // Shrunk towards the camera (its origin here): each point drawn where it was on screen, only nearer.
            pose.scale(toward, toward, toward);
            boolean others = level == null;
            if (level != null) {
                long now = Util.getMillis();
                sweep(now);
                // World-lit models first: their light and shade are in their vertices, so the lights leave them be.
                brightLights();
                for (int group = 0; group < groups.size(); group++) {
                    int argb = argbs == null ? -1 : argbs[group];
                    if (argbs != null && (argb >>> 24) == 0) continue;
                    for (GhostBlocks.Placed block : groups.get(group)) {
                        if (block.state().getRenderShape() != RenderShape.MODEL) {
                            others = true;
                            continue;
                        }
                        LitBlock lit = lit(minecraft, level, block, now);
                        if (lit.vertices() == 0) continue;
                        pose.pushPose();
                        // From the camera, in floats: the block's place less the camera's stays small anywhere in the world.
                        pose.translate(block.pos().getX() - eye.x + 0.5, block.pos().getY() - eye.y + 0.5, block.pos().getZ() - eye.z + 0.5);
                        pose.scale(size, size, size);
                        pose.translate(-0.5F, -0.5F, -0.5F);
                        boolean chunk = lit.type == RenderType.solid() || lit.type == RenderType.cutoutMipped() || lit.type == RenderType.cutout();
                        lit.replay(buffers.getBuffer(chunk ? lit.type : Sheets.translucentCullBlockSheet()), pose.last().pose(), argb);
                        pose.popPose();
                    }
                }
                buffers.endBatch();
            }
            if (!others) return;
            // The rest: models drawn as an item draws a block, and blocks drawn by their block entity's renderer.
            if (light == GhostBlocks.Light.BRIGHT) brightLights();
            else Lighting.setupLevel();
            for (int group = 0; group < groups.size(); group++) {
                if (argbs != null && (argbs[group] >>> 24) == 0) continue;
                for (GhostBlocks.Placed block : groups.get(group)) {
                    if (level != null && block.state().getRenderShape() == RenderShape.MODEL) continue;
                    pose.pushPose();
                    pose.translate(block.pos().getX() - eye.x + 0.5, block.pos().getY() - eye.y + 0.5, block.pos().getZ() - eye.z + 0.5);
                    pose.scale(size, size, size);
                    pose.translate(-0.5F, -0.5F, -0.5F);
                    // (A block entity's model, lit like the world: by the light where it stands.)
                    int lightCoords = level != null ? LevelRenderer.getLightColor(level, block.pos()) : FULL_BRIGHT;
                    minecraft.getBlockRenderer().renderSingleBlock(block.state(), pose, buffers, lightCoords, OverlayTexture.NO_OVERLAY);
                    pose.popPose();
                }
            }
            buffers.endBatch();
        } finally {
            Lighting.setupLevel();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static void brightLights() {
        float strength = LIGHT_STRENGTH / (float) Math.sqrt(3.0);
        RenderSystem.setShaderLights(new Vector3f(strength, strength, strength), new Vector3f(-strength, -strength, -strength));
    }

    // A block as the world lights it where it stands: its vertices (from its own corner, with its model's offset in the
    // block), each with its colour and light, built when it's first drawn there, again when it changes, and every
    // LIT_MILLIS. Drawn in its chunk layer.
    private static final class LitBlock implements VertexConsumer {
        private BlockState state;
        private long builtAt;
        private long drawnAt;
        private RenderType type = RenderType.solid();
        // x, y, z, u, v for each vertex; and its colour and light.
        private final FloatArrayList vertices = new FloatArrayList();
        private final IntArrayList colors = new IntArrayList();
        private final IntArrayList lights = new IntArrayList();
        private final Vector4f at = new Vector4f();

        private LitBlock(BlockState state) {
            this.state = state;
        }

        int vertices() {
            return colors.size();
        }

        void build(Minecraft minecraft, ClientLevel level, GhostBlocks.Placed block) {
            vertices.clear();
            colors.clear();
            lights.clear();
            type = ItemBlockRenderTypes.getChunkRenderType(block.state());
            minecraft.getBlockRenderer().getModelRenderer().tesselateBlock(level, minecraft.getBlockRenderer().getBlockModel(block.state()), block.state(),
                    block.pos(), new PoseStack(), this, true, RANDOM, block.state().getSeed(block.pos()), OverlayTexture.NO_OVERLAY);
        }

        // Its vertices into a buffer, placed by the pose; with a group's colour and opacity multiplied in (-1 for none).
        void replay(VertexConsumer consumer, Matrix4f pose, int argb) {
            for (int i = 0, n = colors.size(); i < n; i++) {
                int p = i * 5;
                pose.transform(at.set(vertices.getFloat(p), vertices.getFloat(p + 1), vertices.getFloat(p + 2), 1.0F));
                int color = argb == -1 ? colors.getInt(i) : FastColor.ARGB32.multiply(colors.getInt(i), argb);
                consumer.addVertex(at.x, at.y, at.z, color, vertices.getFloat(p + 3), vertices.getFloat(p + 4), OverlayTexture.NO_OVERLAY, lights.getInt(i),
                        DIAGONAL, DIAGONAL, DIAGONAL);
            }
        }

        // ---- Taking down what tesselateBlock makes (through putBulkData, which adds whole vertices) ----

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
            vertices.add(x);
            vertices.add(y);
            vertices.add(z);
            vertices.add(u);
            vertices.add(v);
            colors.add(color);
            lights.add(light);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            addVertex(x, y, z, -1, 0.0F, 0.0F, OverlayTexture.NO_OVERLAY, FULL_BRIGHT, 0.0F, 1.0F, 0.0F);
            return this;
        }

        // A vertex built a part at a time: the parts fill in the last one added.
        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            if (!colors.isEmpty()) colors.set(colors.size() - 1, FastColor.ARGB32.color(alpha, red, green, blue));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            int p = vertices.size() - 2;
            if (p >= 0) {
                vertices.set(p, u);
                vertices.set(p + 1, v);
            }
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            if (!lights.isEmpty()) lights.set(lights.size() - 1, u & 0xFFFF | v << 16);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }

    private static LitBlock lit(Minecraft minecraft, ClientLevel level, GhostBlocks.Placed block, long now) {
        LitBlock lit = LIT.computeIfAbsent(block.pos().asLong(), key -> new LitBlock(block.state()));
        lit.drawnAt = now;
        if (lit.state == block.state() && lit.builtAt != 0L && now - lit.builtAt < LIT_MILLIS) return lit;
        lit.state = block.state();
        lit.builtAt = now;
        lit.build(minecraft, level, block);
        return lit;
    }

    // Forgets the blocks not drawn for a while (looked at once a second).
    private static void sweep(long now) {
        if (now - lastSwept < 1000) return;
        lastSwept = now;
        LIT.values().removeIf(lit -> now - lit.drawnAt > FORGET_MILLIS);
    }

    // The ghosts' picture over the world, where the world's depth lets it show.
    private static void composite(RenderTarget main, TextureTarget ghosts, ShaderProgram program, int argb) {
        TextureTarget depth = worldDepth(main);
        depth.copyDepthFrom(main);
        main.bindWrite(true);
        begin(program, main, ghosts, depth);
        program.setVec4("ColorModulator", (argb >> 16 & 0xFF) / 255.0F, (argb >> 8 & 0xFF) / 255.0F, (argb & 0xFF) / 255.0F, (argb >>> 24) / 255.0F);
        end(main, 2);
    }

    // The effect's shader over the world, where the ghosts show, given where each pixel is in the world.
    private static void effectComposite(Minecraft minecraft, RenderTarget main, TextureTarget ghosts, ShaderProgram program,
                                        @Nullable ResourceLocation texture, float[] params, float toward) {
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 eye = camera.getPosition();
        // From the screen back to the world, relative to the camera: the level's projection and its view's turning.
        INVERSE.set(RenderSystem.getProjectionMatrix()).mul(new Matrix4f().rotation(camera.rotation().conjugate(new Quaternionf()))).invert();
        // Fetched before drawing: the first time, the texture is loaded.
        int effectTexture = texture != null ? minecraft.getTextureManager().getTexture(texture).getId() : ghosts.getColorTextureId();
        TextureTarget depth = worldDepth(main);
        depth.copyDepthFrom(main);
        main.bindWrite(true);
        begin(program, main, ghosts, depth);
        RenderSystem.activeTexture(33987);
        RenderSystem.bindTexture(effectTexture);
        program.setSampler("EffectSampler", 3);
        program.setVec4("ColorModulator", 1.0F, 1.0F, 1.0F, 1.0F);
        program.setMatrix("GhostInverseViewProjection", INVERSE);
        program.setVec4("GhostCamera", (float) eye.x, (float) eye.y, (float) eye.z, toward);
        for (int i = 0; i < 4; i++) {
            program.setVec4(PARAMS[i], param(params, i * 4), param(params, i * 4 + 1), param(params, i * 4 + 2), param(params, i * 4 + 3));
        }
        end(main, 3);
    }

    private static final String[] PARAMS = {"GhostParams0", "GhostParams1", "GhostParams2", "GhostParams3"};

    private static void begin(ShaderProgram program, RenderTarget main, TextureTarget ghosts, TextureTarget depth) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        program.use();
        RenderSystem.activeTexture(33984);
        RenderSystem.bindTexture(ghosts.getColorTextureId());
        RenderSystem.activeTexture(33985);
        RenderSystem.bindTexture(ghosts.getDepthTextureId());
        RenderSystem.activeTexture(33986);
        RenderSystem.bindTexture(depth.getDepthTextureId());
        program.setSampler("GhostSampler", 0);
        program.setSampler("GhostDepthSampler", 1);
        program.setSampler("DepthSampler", 2);
        program.setMatrix("ProjMat", ORTHO.setOrtho(0.0F, main.width, 0.0F, main.height, 0.1F, 1000.0F));
        program.setVec2("OutSize", main.width, main.height);
    }

    private static void end(RenderTarget main, int lastUnit) {
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        builder.addVertex(0.0F, 0.0F, 500.0F);
        builder.addVertex(main.width, 0.0F, 500.0F);
        builder.addVertex(main.width, main.height, 500.0F);
        builder.addVertex(0.0F, main.height, 500.0F);
        BufferUploader.draw(builder.buildOrThrow());
        for (int unit = lastUnit; unit >= 0; unit--) {
            RenderSystem.activeTexture(33984 + unit);
            RenderSystem.bindTexture(0);
        }
        ShaderProgram.release();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static float param(float[] params, int index) {
        return index < params.length ? params[index] : 0.0F;
    }

    private static @Nullable ShaderProgram program(GhostBlocks.Show show, boolean alpha) {
        int key = show.ordinal() * 2 + (alpha ? 1 : 0);
        ShaderProgram program = PROGRAMS.get(key);
        if (program != null || FAILED.contains(FRAGMENT_SHADER.toString())) return program;
        try {
            program = ShaderProgram.compile(Minecraft.getInstance().getResourceManager(), VERTEX_SHADER, FRAGMENT_SHADER,
                    List.of("GHOST_MODE " + show.ordinal(), "GHOST_ALPHA " + (alpha ? 1 : 0)), List.of("Position"));
        } catch (IOException e) {
            CoolCatCanvas.LOGGER.error("Couldn't compile the ghost blocks shader", e);
            FAILED.add(FRAGMENT_SHADER.toString());
            return null;
        }
        PROGRAMS.put(key, program);
        return program;
    }

    private static @Nullable ShaderProgram effect(GhostBlocks.Show show, ResourceLocation shader) {
        String key = shader + "/" + show.name();
        ShaderProgram program = EFFECTS.get(key);
        if (program != null || FAILED.contains(key)) return program;
        try {
            program = ShaderProgram.compile(Minecraft.getInstance().getResourceManager(), VERTEX_SHADER, shader,
                    List.of("GHOST_MODE " + show.ordinal()), List.of("Position"));
        } catch (IOException e) {
            CoolCatCanvas.LOGGER.error("Couldn't compile the ghost effect shader {}", shader, e);
            FAILED.add(key);
            return null;
        }
        EFFECTS.put(key, program);
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
}
