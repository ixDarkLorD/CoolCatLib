package net.ixdarklord.coolcatcanvas.api.client.gui.components.widgets.panel;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.ApiStatus;
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
    // How far each edge fade (left, right, top, bottom) is out, eased so it slides into its side at the content's end.
    private final float[] edgeExtent = new float[4];
    private long lastEdgeFrame = -1L;

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
    protected abstract void renderWorld(GuiGraphics graphics, double mouseWorldX, double mouseWorldY, boolean mouseInView, float partialTick);

    /** A press released without dragging. */
    protected boolean worldClicked(double worldX, double worldY, int button) {
        return false;
    }

    /** Screen-space background under the world (clipped to the panel). */
    protected void renderViewBackground(GuiGraphics graphics) {}

    /** Screen-space overlay on top of the world (not clipped); for tooltips, labels... */
    protected void renderViewForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

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
     * hinting that there's more to pan to. Each fade shrinks into its side as the end of the content on that side
     * comes into view, and slides back out when there's more again. {@code size} 0 disables it.
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
    protected final void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ScreenRectangle b = this.getBounds();
        if (b.width() <= 0 || b.height() <= 0) return;

        if (this.fitPending) {
            this.fitPending = false;
            this.fitNow();
        }
        this.animateZoom();
        this.clampOffsets();

        graphics.enableScissor(b.left(), b.top(), b.right(), b.bottom());
        this.renderViewBackground(graphics);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate((float) (b.left() - this.offsetX * this.zoom), (float) (b.top() - this.offsetY * this.zoom), 0.0F);
        pose.scale((float) this.zoom, (float) this.zoom, 1.0F);
        boolean mouseInView = !this.dragged && b.containsPoint(mouseX, mouseY);
        this.renderWorld(graphics, this.toWorldX(mouseX), this.toWorldY(mouseY), mouseInView, partialTick);
        pose.popPose();

        graphics.disableScissor();
        this.renderEdgeFades(graphics);
        this.renderViewForeground(graphics, mouseX, mouseY, partialTick);
    }

    private void renderEdgeFades(GuiGraphics graphics) {
        boolean vignette = this.vignetteSize > 0 && this.vignetteStrength > 0;
        if (this.edgeFade <= 0 && !vignette) return;
        ScreenRectangle b = this.getBounds();
        if (vignette) {
            // Batched text is drawn after fills when flushed, so draw what's pending first to cover it.
            graphics.flush();
            int size = Math.min(this.vignetteSize, Math.min(b.width(), b.height()) / 2);
            float strength = this.vignetteStrength;
            drawEdges(graphics, b, 0x000000, size, size, size, size, strength, strength, strength, strength);
        }
        if (this.edgeFade <= 0) return;
        int size = Math.min(this.edgeFade, Math.min(b.width(), b.height()) / 3);
        if (size <= 0) return;
        ScreenRectangle content = this.getContentBounds();
        // How much content is hidden past each edge, as a share of the fade's size: the fade is as wide as that, so
        // it draws back into its side while the last of the content comes into view.
        this.easeEdge(0, hiddenShare(b.left() - this.toScreenX(content.left()), size));
        this.easeEdge(1, hiddenShare(this.toScreenX(content.right()) - b.right(), size));
        this.easeEdge(2, hiddenShare(b.top() - this.toScreenY(content.top()), size));
        this.easeEdge(3, hiddenShare(this.toScreenY(content.bottom()) - b.bottom(), size));
        this.lastEdgeFrame = Util.getNanos();
        float[] e = this.edgeExtent;
        if (e[0] + e[1] + e[2] + e[3] <= 0.0F) return;

        if (!vignette) graphics.flush();
        // The fade shrinks towards its edge; its alpha only drops at the very end so it doesn't leave a hard line.
        drawEdges(graphics, b, this.edgeFadeColor.getAsInt() & 0xFFFFFF,
                Math.round(size * e[0]), Math.round(size * e[1]), Math.round(size * e[2]), Math.round(size * e[3]),
                Math.min(1.0F, e[0] * 3.0F), Math.min(1.0F, e[1] * 3.0F), Math.min(1.0F, e[2] * 3.0F), Math.min(1.0F, e[3] * 3.0F));
    }

    private void easeEdge(int side, float target) {
        double dt = this.lastEdgeFrame < 0 ? 1.0 : Math.min((Util.getNanos() - this.lastEdgeFrame) / 1.0E9, 0.1);
        float value = this.edgeExtent[side] + (target - this.edgeExtent[side]) * (float) (1.0 - Math.exp(-14.0 * dt));
        this.edgeExtent[side] = Math.abs(target - value) < 0.01F ? target : value;
    }

    /** Gradients from each edge inwards, starting at the given alpha and fading to nothing over that side's size. */
    private static void drawEdges(GuiGraphics graphics, ScreenRectangle b, int rgb, int left, int right, int top, int bottom,
                                  float leftAlpha, float rightAlpha, float topAlpha, float bottomAlpha) {
        if (top > 0) graphics.fillGradient(b.left(), b.top(), b.right(), b.top() + top, argb(topAlpha, rgb), argb(0.0F, rgb));
        if (bottom > 0) graphics.fillGradient(b.left(), b.bottom() - bottom, b.right(), b.bottom(), argb(0.0F, rgb), argb(bottomAlpha, rgb));
        for (int i = 0; i < left; i++) {
            graphics.fill(b.left() + i, b.top(), b.left() + i + 1, b.bottom(), argb(leftAlpha * (1.0F - (i + 0.5F) / left), rgb));
        }
        for (int i = 0; i < right; i++) {
            graphics.fill(b.right() - i - 1, b.top(), b.right() - i, b.bottom(), argb(rightAlpha * (1.0F - (i + 0.5F) / right), rgb));
        }
    }

    private static int argb(float alpha, int rgb) {
        return FastColor.ARGB32.color(FastColor.as8BitChannel(alpha), rgb);
    }

    private static float hiddenShare(double hiddenPixels, int size) {
        return (float) Mth.clamp(hiddenPixels / size, 0.0, 1.0);
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
    public boolean mouseClicked(double mouseX, double mouseY, int button, boolean doubleClick) {
        if (super.mouseClicked(mouseX, mouseY, button, doubleClick)) return true;
        if (!this.isMouseOver(mouseX, mouseY)) return false;
        this.pressedButton = button;
        this.pressX = mouseX;
        this.pressY = mouseY;
        this.dragged = false;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (super.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        if (this.pressedButton == -1) return false;
        if (!this.dragged && Math.hypot(mouseX - this.pressX, mouseY - this.pressY) >= DRAG_THRESHOLD) {
            this.dragged = true;
            // Start from the press position so the threshold doesn't eat the first pixels.
            dragX = mouseX - this.pressX;
            dragY = mouseY - this.pressY;
        }
        if (this.dragged) {
            this.offsetX -= dragX / this.zoom;
            this.offsetY -= dragY / this.zoom;
            this.clampOffsets();
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (super.mouseReleased(mouseX, mouseY, button)) return true;
        if (this.pressedButton == -1) return false;
        boolean click = !this.dragged && this.pressedButton == button;
        this.pressedButton = -1;
        this.dragged = false;
        if (click && this.isMouseOver(mouseX, mouseY)) {
            this.worldClicked(this.toWorldX(mouseX), this.toWorldY(mouseY), button);
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        ScreenRectangle b = this.getBounds();
        double cx = b.left() + b.width() / 2.0, cy = b.top() + b.height() / 2.0;
        double pan = 24.0 / this.zoom;
        switch (keyCode) {
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
