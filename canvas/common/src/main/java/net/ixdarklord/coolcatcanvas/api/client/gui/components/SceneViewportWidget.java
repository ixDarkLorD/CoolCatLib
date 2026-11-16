package net.ixdarklord.coolcatcanvas.api.client.gui.components;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.ixdarklord.coolcatcanvas.api.client.viewport.SceneViewport;
import net.ixdarklord.coolcatcanvas.internal.client.viewport.SceneViewportManager;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.ixdarklord.coolcatcanvas.internal.mixin.client.GuiGraphicsExtractorInvoker;
import net.ixdarklord.coolcatcanvas.internal.mixin.client.RenderPipelinesAccessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.sounds.SoundManager;
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
    // The GUI's premultiplied textured blit, with a shader that keeps the pixels the GUI's own drops (alpha 0), where a
    // viewport's glowing sprites add light over nothing of the scene.
    private static final RenderPipeline BLIT = RenderPipeline.builder(RenderPipelinesAccessor.coolcatcanvas$guiTexturedSnippet())
            .withLocation(CoolCatCanvas.rl("pipeline/viewport_blit"))
            .withFragmentShader(CoolCatCanvas.rl("core/viewport_blit"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
            .build();
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
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        viewport.use();
        if (background != 0) graphics.fill(getX(), getY(), getRight(), getBottom(), background);
        GpuTextureView view = SceneViewportManager.view(viewport);
        // It is drawn at the start of the next frame the first time; and upside down, as every texture drawn to is.
        if (view != null) {
            // Premultiplied, as the picture is drawn: translucent edges keep their colour, and a viewport's glowing
            // sprites add their light onto what's behind the widget.
            ((GuiGraphicsExtractorInvoker) graphics).coolcatcanvas$innerBlit(BLIT, view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST),
                    getX(), getY(), getRight(), getBottom(), 0.0F, 1.0F, 1.0F, 0.0F, -1);
        }
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
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
