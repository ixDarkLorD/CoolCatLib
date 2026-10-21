package net.ixdarklord.coolcatcanvas.api.client.gui.components;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.ixdarklord.coolcatcanvas.api.client.viewport.SceneViewport;
import net.ixdarklord.coolcatcanvas.internal.client.viewport.SceneViewportManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.ApiStatus;

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
        if (background != 0) graphics.fill(getX(), getY(), getX() + width, getY() + height, background);
        int texture = SceneViewportManager.textureId(viewport);
        // It is drawn at the start of the next frame the first time; and upside down, as every texture drawn to is.
        if (texture >= 0) {
            graphics.flush();
            RenderSystem.setShaderTexture(0, texture);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.enableBlend();
            org.joml.Matrix4f pose = graphics.pose().last().pose();
            int right = getX() + width;
            int bottom = getY() + height;
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            builder.vertex(pose, getX(), getY(), 0.0F).uv(0.0F, 1.0F).endVertex();
            builder.vertex(pose, getX(), bottom, 0.0F).uv(0.0F, 0.0F).endVertex();
            builder.vertex(pose, right, bottom, 0.0F).uv(1.0F, 0.0F).endVertex();
            builder.vertex(pose, right, getY(), 0.0F).uv(1.0F, 1.0F).endVertex();
            BufferUploader.drawWithShader(builder.end());
            RenderSystem.disableBlend();
        }
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        if (turnable) viewport.camera(viewport.yaw() + (float) dragX * 1.2F, viewport.pitch() + (float) dragY * 1.2F);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!zoomable || !isMouseOver(mouseX, mouseY)) return false;
        viewport.zoom(viewport.zoom() * (scrollY > 0 ? 1.15F : 1.0F / 1.15F));
        return true;
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager soundManager) {
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
