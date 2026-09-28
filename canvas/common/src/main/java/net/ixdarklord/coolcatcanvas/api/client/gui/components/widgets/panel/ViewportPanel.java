package net.ixdarklord.coolcatcanvas.api.client.gui.components.widgets.panel;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jetbrains.annotations.ApiStatus;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.function.IntSupplier;

/**
 * A panel showing a 2D "world" that can be panned by dragging and zoomed with the mouse wheel
 * (anchored at the cursor, smoothly animated). Subclasses draw in world coordinates and describe the
 * extent of their content, which the view is kept within.
 * <p>
 * Controls: drag (any button) to pan, wheel to zoom, {@code +}/{@code -} to zoom, {@code 0}/{@code Home} to fit,
 * arrow keys to pan. A press that isn't dragged counts as a click and goes to {@link #worldClicked}.
 */
@ApiStatus.Experimental
public abstract class ViewportPanel extends Panel {
    private static final double DRAG_THRESHOLD = 3.0;
    private static final double ZOOM_SMOOTHING = 18.0;

    private double offsetX, offsetY;
    private double zoom = 1.0, targetZoom = 1.0;
    private double anchorX, anchorY;
    private double minZoom = 0.5, maxZoom = 2.5, zoomStep = 1.2;
    private int margin = 16;
    private boolean fitPending;
    private int vignetteSize;
    private float vignetteStrength;
    private int edgeFade;
    private IntSupplier edgeFadeColor = () -> 0xFF000000;

    private int pressedButton = -1;
    private double pressX, pressY;
    private boolean dragged;
    private long lastFrame = -1L;

    // --- To implement ---

    /** The area (in world coordinates) that holds content; the view can't be panned far away from it. */
    protected abstract ScreenRectangle getContentBounds();

    /**
     * Draws the world. The pose is already translated/scaled; draw at world coordinates.
     *
     * @param mouseInView false while panning or when the cursor is outside the panel, so hover effects can be skipped
     */
    protected abstract void extractWorld(GuiGraphicsExtractor graphics, double mouseWorldX, double mouseWorldY, boolean mouseInView, float partialTick);

    /** A press released without dragging. */
    protected boolean worldClicked(double worldX, double worldY, MouseButtonEvent event) {
        return false;
    }

    /** Screen-space background under the world (clipped to the panel). */
    protected void extractViewBackground(GuiGraphicsExtractor graphics) {}

    /** Screen-space overlay on top of the world (not clipped); for tooltips, labels... */
    protected void extractViewForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

    // --- Configuration ---

    public ViewportPanel setZoomLimits(double min, double max) {
        this.minZoom = Math.min(min, max);
        this.maxZoom = Math.max(min, max);
        this.targetZoom = Mth.clamp(this.targetZoom, this.minZoom, this.maxZoom);
        this.zoom = Mth.clamp(this.zoom, this.minZoom, this.maxZoom);
        return this;
    }

    /** Zoom factor applied per wheel notch. */
    public ViewportPanel setZoomStep(double step) {
        this.zoomStep = Math.max(1.01, step);
        return this;
    }

    /** A subtle dark vignette along every edge ({@code strength} is the alpha at the very edge; 0 disables it). */
    public ViewportPanel setVignette(int size, float strength) {
        this.vignetteSize = Math.max(0, size);
        this.vignetteStrength = Mth.clamp(strength, 0.0F, 1.0F);
        return this;
    }

    /**
     * Fades the edges of the view into {@code color} (the panel's background) wherever content continues past them,
     * hinting that there's more to pan to. {@code size} 0 disables it.
     */
    public ViewportPanel setEdgeFade(int size, IntSupplier color) {
        this.edgeFade = Math.max(0, size);
        this.edgeFadeColor = color;
        return this;
    }

    /** Extra world space allowed around the content bounds. */
    public ViewportPanel setMargin(int margin) {
        this.margin = Math.max(0, margin);
        return this;
    }

    // --- View state ---

    public double getZoom() {
        return this.zoom;
    }

    /**
     * The lowest zoom allowed right now: the configured minimum, or less if that's what it takes to fit
     * the whole content in the view.
     */
    public double getMinZoom() {
        return Math.min(this.minZoom, this.getFitZoom());
    }

    private double getFitZoom() {
        ScreenRectangle content = this.getContentBounds();
        ScreenRectangle b = this.getBounds();
        if (content.width() <= 0 || content.height() <= 0 || b.width() <= 0 || b.height() <= 0) return this.minZoom;
        return Math.min(b.width() / (double) (content.width() + this.margin * 2), b.height() / (double) (content.height() + this.margin * 2));
    }

    public double getMaxZoom() {
        return this.maxZoom;
    }

    /** Zoom mapped onto [0, 1] logarithmically (so each step feels the same); handy for sliders. */
    public double getZoomProgress() {
        double min = this.getMinZoom();
        return Math.log(this.targetZoom / min) / Math.log(this.maxZoom / min);
    }

    public void setZoomProgress(double progress) {
        double min = this.getMinZoom();
        double zoom = min * Math.pow(this.maxZoom / min, Mth.clamp(progress, 0.0, 1.0));
        ScreenRectangle b = this.getBounds();
        this.zoomTo(zoom, b.left() + b.width() / 2.0, b.top() + b.height() / 2.0, false);
    }

    /** Zooms to {@code zoom}, keeping the world point under the given screen position fixed. */
    public void zoomTo(double zoom, double screenX, double screenY, boolean animate) {
        this.targetZoom = Mth.clamp(zoom, this.getMinZoom(), this.maxZoom);
        this.anchorX = screenX;
        this.anchorY = screenY;
        if (!animate) this.applyZoom(this.targetZoom);
    }

    public void zoomBy(double factor, double screenX, double screenY) {
        this.zoomTo(this.targetZoom * factor, screenX, screenY, true);
    }

    /** Centers the view on a world position. */
    public void centerOn(double worldX, double worldY) {
        ScreenRectangle b = this.getBounds();
        this.offsetX = worldX - b.width() / (2.0 * this.zoom);
        this.offsetY = worldY - b.height() / (2.0 * this.zoom);
        this.clampOffsets();
    }

    /** Fits the whole content in the view (within the zoom limits) on the next frame. */
    public void requestFit() {
        this.fitPending = true;
    }

    public boolean isPanning() {
        return this.dragged;
    }

    public double toWorldX(double screenX) {
        return this.offsetX + (screenX - this.getBounds().left()) / this.zoom;
    }

    public double toWorldY(double screenY) {
        return this.offsetY + (screenY - this.getBounds().top()) / this.zoom;
    }

    public double toScreenX(double worldX) {
        return this.getBounds().left() + (worldX - this.offsetX) * this.zoom;
    }

    public double toScreenY(double worldY) {
        return this.getBounds().top() + (worldY - this.offsetY) * this.zoom;
    }

    // --- Rendering ---

    @Override
    protected void onResized() {
        this.clampOffsets();
    }

    @Override
    protected final void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ScreenRectangle b = this.getBounds();
        if (b.width() <= 0 || b.height() <= 0) return;

        if (this.fitPending) {
            this.fitPending = false;
            this.fitNow();
        }
        this.animateZoom();
        this.clampOffsets();

        graphics.enableScissor(b.left(), b.top(), b.right(), b.bottom());
        this.extractViewBackground(graphics);

        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate((float) (b.left() - this.offsetX * this.zoom), (float) (b.top() - this.offsetY * this.zoom));
        pose.scale((float) this.zoom, (float) this.zoom);
        boolean mouseInView = !this.dragged && b.containsPoint(mouseX, mouseY);
        this.extractWorld(graphics, this.toWorldX(mouseX), this.toWorldY(mouseY), mouseInView, partialTick);
        pose.popMatrix();

        graphics.disableScissor();
        this.extractEdgeFades(graphics);
        this.extractViewForeground(graphics, mouseX, mouseY, partialTick);
    }

    private void extractEdgeFades(GuiGraphicsExtractor graphics) {
        boolean vignette = this.vignetteSize > 0 && this.vignetteStrength > 0;
        if (this.edgeFade <= 0 && !vignette) return;
        ScreenRectangle b = this.getBounds();
        if (vignette) {
            // Within a stratum text and items draw after fills, so the overlays need their own to cover them.
            graphics.nextStratum();
            drawEdges(graphics, b, Math.min(this.vignetteSize, Math.min(b.width(), b.height()) / 2), 0x000000,
                    this.vignetteStrength, this.vignetteStrength, this.vignetteStrength, this.vignetteStrength);
        }
        if (this.edgeFade <= 0) return;
        ScreenRectangle content = this.getContentBounds();
        // How much content is hidden past each edge, ramped over the first 8px so the fade comes in smoothly.
        float left = hiddenRamp(b.left() - this.toScreenX(content.left()));
        float right = hiddenRamp(this.toScreenX(content.right()) - b.right());
        float top = hiddenRamp(b.top() - this.toScreenY(content.top()));
        float bottom = hiddenRamp(this.toScreenY(content.bottom()) - b.bottom());
        if (left + right + top + bottom <= 0.0F) return;

        if (!vignette) graphics.nextStratum();
        drawEdges(graphics, b, Math.min(this.edgeFade, Math.min(b.width(), b.height()) / 3),
                this.edgeFadeColor.getAsInt() & 0xFFFFFF, left, right, top, bottom);
    }

    /** Gradients from each edge inwards, starting at the given alpha and fading to nothing over {@code size}. */
    private static void drawEdges(GuiGraphicsExtractor graphics, ScreenRectangle b, int size, int rgb, float left, float right, float top, float bottom) {
        if (size <= 0) return;
        if (top > 0) graphics.fillGradient(b.left(), b.top(), b.right(), b.top() + size, ARGB.color(top, rgb), ARGB.color(0.0F, rgb));
        if (bottom > 0) graphics.fillGradient(b.left(), b.bottom() - size, b.right(), b.bottom(), ARGB.color(0.0F, rgb), ARGB.color(bottom, rgb));
        for (int i = 0; i < size; i++) {
            float falloff = 1.0F - (i + 0.5F) / size;
            if (left > 0) graphics.fill(b.left() + i, b.top(), b.left() + i + 1, b.bottom(), ARGB.color(left * falloff, rgb));
            if (right > 0) graphics.fill(b.right() - i - 1, b.top(), b.right() - i, b.bottom(), ARGB.color(right * falloff, rgb));
        }
    }

    private static float hiddenRamp(double hiddenPixels) {
        return (float) Mth.clamp(hiddenPixels / 8.0, 0.0, 1.0);
    }

    private void fitNow() {
        ScreenRectangle content = this.getContentBounds();
        if (content.width() <= 0 || content.height() <= 0) return;

        // Everything visible, but never enlarged past 1:1.
        this.zoom = this.targetZoom = Math.min(Math.min(this.getFitZoom(), 1.0), this.maxZoom);
        this.centerOn(content.left() + content.width() / 2.0, content.top() + content.height() / 2.0);
    }

    private void animateZoom() {
        long now = Util.getNanos();
        double dt = this.lastFrame < 0 ? 0.0 : (now - this.lastFrame) / 1.0E9;
        this.lastFrame = now;
        if (this.zoom == this.targetZoom) return;

        double t = 1.0 - Math.exp(-ZOOM_SMOOTHING * Math.min(dt, 0.1));
        double next = this.zoom + (this.targetZoom - this.zoom) * t;
        if (Math.abs(this.targetZoom - next) < 1.0E-3) next = this.targetZoom;
        this.applyZoom(next);
    }

    private void applyZoom(double newZoom) {
        double worldX = this.toWorldX(this.anchorX);
        double worldY = this.toWorldY(this.anchorY);
        this.zoom = newZoom;
        this.offsetX = worldX - (this.anchorX - this.getBounds().left()) / this.zoom;
        this.offsetY = worldY - (this.anchorY - this.getBounds().top()) / this.zoom;
        this.clampOffsets();
    }

    /** Keeps the content in view; content smaller than the view is centered on that axis. */
    private void clampOffsets() {
        ScreenRectangle content = this.getContentBounds();
        ScreenRectangle b = this.getBounds();
        double viewW = b.width() / this.zoom, viewH = b.height() / this.zoom;
        this.offsetX = clampAxis(this.offsetX, content.left() - this.margin, content.right() + this.margin, viewW);
        this.offsetY = clampAxis(this.offsetY, content.top() - this.margin, content.bottom() + this.margin, viewH);
    }

    private static double clampAxis(double offset, double min, double max, double view) {
        if (max - min <= view) return (min + max - view) / 2.0;
        return Mth.clamp(offset, min, max - view);
    }

    // --- Input ---

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (!this.isMouseOver(event.x(), event.y())) return false;
        this.pressedButton = event.button();
        this.pressX = event.x();
        this.pressY = event.y();
        this.dragged = false;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (super.mouseDragged(event, dragX, dragY)) return true;
        if (this.pressedButton == -1) return false;
        if (!this.dragged && Math.hypot(event.x() - this.pressX, event.y() - this.pressY) >= DRAG_THRESHOLD) {
            this.dragged = true;
            // Start from the press position so the threshold doesn't eat the first pixels.
            dragX = event.x() - this.pressX;
            dragY = event.y() - this.pressY;
        }
        if (this.dragged) {
            this.offsetX -= dragX / this.zoom;
            this.offsetY -= dragY / this.zoom;
            this.clampOffsets();
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (super.mouseReleased(event)) return true;
        if (this.pressedButton == -1) return false;
        boolean click = !this.dragged && this.pressedButton == event.button();
        this.pressedButton = -1;
        this.dragged = false;
        if (click && this.isMouseOver(event.x(), event.y())) {
            this.worldClicked(this.toWorldX(event.x()), this.toWorldY(event.y()), event);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0 || !this.isMouseOver(mouseX, mouseY)) return false;
        this.zoomBy(Math.pow(this.zoomStep, scrollY), mouseX, mouseY);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        ScreenRectangle b = this.getBounds();
        double cx = b.left() + b.width() / 2.0, cy = b.top() + b.height() / 2.0;
        double pan = 24.0 / this.zoom;
        switch (event.key()) {
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> this.zoomBy(this.zoomStep, cx, cy);
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> this.zoomBy(1.0 / this.zoomStep, cx, cy);
            case GLFW.GLFW_KEY_0, GLFW.GLFW_KEY_KP_0, GLFW.GLFW_KEY_HOME -> this.requestFit();
            case GLFW.GLFW_KEY_LEFT -> this.offsetX -= pan;
            case GLFW.GLFW_KEY_RIGHT -> this.offsetX += pan;
            case GLFW.GLFW_KEY_UP -> this.offsetY -= pan;
            case GLFW.GLFW_KEY_DOWN -> this.offsetY += pan;
            default -> {
                return false;
            }
        }
        this.clampOffsets();
        return true;
    }
}
