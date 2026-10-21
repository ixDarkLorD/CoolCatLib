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
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.ixdarklord.coolcatcanvas.api.client.vfx.GhostBlocks;
import net.ixdarklord.coolcatcanvas.internal.client.render.CanvasProgram;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// Draws GhostBlocks: the blocks opaque, with the level's camera, into a screen-sized target with its own depth; then
// that picture laid over the world at one opacity, where the world's depth lets it show (shader core/ghost: GHOST_MODE
// 0 through walls, 1 in the world, 2 always). Seen through walls, each block is drawn a hair larger than itself, so
// where it is in plain sight its ghost is just in front of the real block and leaves it showing; in the world, a hair
// smaller, so it doesn't fight a real block beside it. The world's depth is copied first, as the main target can't be
// read while it is drawn to.
public final class GhostRenderer {
    private static final float GROW = 1.02F;
    private static final float SHRINK = 0.998F;
    private static final int FULL_BRIGHT = 0xF000F0;
    // Two lights this strong, each way along a diagonal, light any face full: 0.4 + 0.6 * (l0 . n + l1 . n), capped at 1.
    private static final float LIGHT_STRENGTH = 10.0F;
    private static final ResourceLocation VERTEX_SHADER = new ResourceLocation("minecraft", "shaders/program/sobel.vsh");
    private static final ResourceLocation FRAGMENT_SHADER = CoolCatCanvas.rl("shaders/core/ghost.fsh");
    private static final Map<GhostBlocks.Show, CanvasProgram> PROGRAMS = new EnumMap<>(GhostBlocks.Show.class);
    private static boolean failed;

    private static @Nullable TextureTarget target;
    private static @Nullable TextureTarget worldDepth;

    private GhostRenderer() {}

    // Nothing kept between frames but the targets and programs: the game's block renderer draws each block itself.
    public static void clearModels() {
    }

    // The shaders were reloaded (or the game is closing): the programs are compiled again when next needed.
    public static void close() {
        PROGRAMS.values().forEach(CanvasProgram::close);
        PROGRAMS.clear();
        failed = false;
    }

    public static void draw(Collection<GhostBlocks.Placed> blocks, int argb, GhostBlocks.Show show, boolean shaded) {
        if (blocks.isEmpty() || (argb >>> 24) == 0) return;
        RenderSystem.assertOnRenderThread();
        CanvasProgram program = program(show);
        if (program == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget main = minecraft.getMainRenderTarget();
        TextureTarget ghosts = target(main);
        float size = show == GhostBlocks.Show.THROUGH_WALLS ? GROW : SHRINK;
        drawBlocks(minecraft, ghosts, blocks, size, shaded);
        composite(main, ghosts, program, argb);
    }

    // The blocks, opaque, with the camera the level was drawn with.
    private static void drawBlocks(Minecraft minecraft, TextureTarget ghosts, Collection<GhostBlocks.Placed> blocks, float size, boolean shaded) {
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
            if (shaded) {
                Lighting.setupLevel(modelView.last().pose());
            } else {
                float strength = LIGHT_STRENGTH / (float) Math.sqrt(3.0);
                RenderSystem.setShaderLights(new Vector3f(strength, strength, strength), new Vector3f(-strength, -strength, -strength));
            }
            MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
            PoseStack pose = new PoseStack();
            for (GhostBlocks.Placed block : blocks) {
                pose.pushPose();
                // From the camera, in floats: the block's place less the camera's stays small anywhere in the world.
                pose.translate(block.pos().getX() - eye.x + 0.5, block.pos().getY() - eye.y + 0.5, block.pos().getZ() - eye.z + 0.5);
                pose.scale(size, size, size);
                pose.translate(-0.5F, -0.5F, -0.5F);
                minecraft.getBlockRenderer().renderSingleBlock(block.state(), pose, buffers, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
            buffers.endBatch();
        } finally {
            Lighting.setupLevel(modelView.last().pose());
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    // The ghosts' picture over the world, where the world's depth lets it show.
    private static void composite(RenderTarget main, TextureTarget ghosts, CanvasProgram program, int argb) {
        TextureTarget depth = worldDepth(main);
        depth.copyDepthFrom(main);
        main.bindWrite(true);
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
        program.setInt("GhostSampler", 0);
        program.setInt("GhostDepthSampler", 1);
        program.setInt("DepthSampler", 2);
        program.setVec4("ColorModulator", (argb >> 16 & 0xFF) / 255.0F, (argb >> 8 & 0xFF) / 255.0F, (argb & 0xFF) / 255.0F, (argb >>> 24) / 255.0F);
        program.setMat4("ProjMat", new Matrix4f().setOrtho(0.0F, main.width, 0.0F, main.height, 0.1F, 1000.0F));
        program.setVec2("OutSize", main.width, main.height);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        builder.vertex(0.0, 0.0, 500.0).endVertex();
        builder.vertex(main.width, 0.0, 500.0).endVertex();
        builder.vertex(main.width, main.height, 500.0).endVertex();
        builder.vertex(0.0, main.height, 500.0).endVertex();
        BufferUploader.draw(builder.end());
        for (int unit = 2; unit >= 0; unit--) {
            RenderSystem.activeTexture(33984 + unit);
            RenderSystem.bindTexture(0);
        }
        CanvasProgram.unuse();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static @Nullable CanvasProgram program(GhostBlocks.Show show) {
        CanvasProgram program = PROGRAMS.get(show);
        if (program != null || failed) return program;
        try {
            program = CanvasProgram.compile(Minecraft.getInstance().getResourceManager(), "ghost blocks", VERTEX_SHADER, FRAGMENT_SHADER,
                    List.of("GHOST_MODE " + show.ordinal()));
        } catch (IOException e) {
            CoolCatCanvas.LOGGER.error("Couldn't compile the ghost blocks shader", e);
            failed = true;
            return null;
        }
        PROGRAMS.put(show, program);
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
