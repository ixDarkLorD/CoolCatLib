package net.ixdarklord.coolcatcanvas.api.client.gui.components;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.ixdarklord.coolcatcanvas.api.client.viewport.SceneViewport;
import net.ixdarklord.coolcatcanvas.internal.client.effect.ShaderProgram;
import net.ixdarklord.coolcatcanvas.internal.client.viewport.SceneViewportManager;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.List;

/**
 * A {@link SceneViewport} in a screen: a 3D view of blocks, a structure or entities, kept inside the widget's
 * rectangle. Drag it to turn the scene, scroll over it to zoom.
 * <pre>{@code
 * SceneViewport view = SceneViewport.create(MyMod.id("preview"), 256, 256);
 * view.blocks(structureBlocks);
 * addRenderableWidget(new SceneViewportWidget(x, y, 128, 128, view));
 * // when the screen closes:
 * view.close();
 * }</pre>
 * The widget does not own the viewport: close it yourself. For a sharp picture, make the viewport as large as the
 * widget is on screen (its size times the GUI scale).
 */
@ApiStatus.Experimental
public class SceneViewportWidget extends AbstractWidget {
    // The picture shown premultiplied, with a shader that keeps the pixels the GUI's own textured shader drops (alpha
    // 0), where a viewport's glowing sprites add light over nothing of the scene. Compiled when first needed.
    private static @Nullable ShaderProgram blit;
    private static boolean blitFailed;
    private final SceneViewport viewport;
    private boolean turnable = true, zoomable = true;
    private int background;

    public SceneViewportWidget(int x, int y, int width, int height, SceneViewport viewport) {
        super(x, y, width, height, Component.empty());
        this.viewport = viewport;
    }

    public SceneViewport viewport() {
        return viewport;
    }

    /** Whether dragging turns the scene and scrolling zooms it. Both by default. */
    public SceneViewportWidget controls(boolean turnable, boolean zoomable) {
        this.turnable = turnable;
        this.zoomable = zoomable;
        return this;
    }

    /** A colour (ARGB) filled behind the scene; 0 for none, which is the default. */
    public SceneViewportWidget background(int argb) {
        this.background = argb;
        return this;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        viewport.use();
        if (background != 0) graphics.fill(getX(), getY(), getRight(), getBottom(), background);
        int texture = SceneViewportManager.textureId(viewport);
        // It is drawn at the start of the next frame the first time; and upside down, as every texture drawn to is.
        if (texture >= 0) {
            graphics.flush();
            Matrix4f pose = graphics.pose().last().pose();
            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            builder.addVertex(pose, getX(), getY(), 0.0F).setUv(0.0F, 1.0F);
            builder.addVertex(pose, getX(), getBottom(), 0.0F).setUv(0.0F, 0.0F);
            builder.addVertex(pose, getRight(), getBottom(), 0.0F).setUv(1.0F, 0.0F);
            builder.addVertex(pose, getRight(), getY(), 0.0F).setUv(1.0F, 1.0F);
            RenderSystem.enableBlend();
            ShaderProgram program = blit();
            if (program == null) {
                RenderSystem.setShaderTexture(0, texture);
                RenderSystem.setShader(GameRenderer::getPositionTexShader);
                BufferUploader.drawWithShader(builder.buildOrThrow());
            } else {
                // Premultiplied, as the picture is drawn: translucent edges keep their colour, and a viewport's glowing
                // sprites add their light onto what's behind the widget.
                RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                        GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                program.use();
                RenderSystem.activeTexture(33984);
                RenderSystem.bindTexture(texture);
                program.setSampler("Sampler0", 0);
                program.setMatrix("ProjMat", RenderSystem.getProjectionMatrix());
                program.setMatrix("ModelViewMat", RenderSystem.getModelViewMatrix());
                float[] color = RenderSystem.getShaderColor();
                program.setVec4("ColorModulator", color[0], color[1], color[2], color[3]);
                BufferUploader.draw(builder.buildOrThrow());
                RenderSystem.bindTexture(0);
                ShaderProgram.release();
                RenderSystem.defaultBlendFunc();
            }
            RenderSystem.disableBlend();
        }
    }

    private static @Nullable ShaderProgram blit() {
        if (blit != null || blitFailed) return blit;
        try {
            blit = ShaderProgram.compile(Minecraft.getInstance().getResourceManager(), CoolCatCanvas.rl("gui/viewport_blit"),
                    CoolCatCanvas.rl("gui/viewport_blit"), List.of(), List.of("Position", "UV0"));
        } catch (IOException e) {
            CoolCatCanvas.LOGGER.error("Couldn't compile the scene viewport shader; viewports are shown without their sprites' light", e);
            blitFailed = true;
        }
        return blit;
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        if (turnable) viewport.camera(viewport.yaw() + (float) dragX * 1.2F, viewport.pitch() + (float) dragY * 1.2F);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!zoomable || !isMouseOver(mouseX, mouseY)) return false;
        viewport.zoom(viewport.zoom() * (scrollY > 0 ? 1.15F : 1.0F / 1.15F));
        return true;
    }

    @Override
    public void playDownSound(SoundManager soundManager) {
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
