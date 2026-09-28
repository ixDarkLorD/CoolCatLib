package net.ixdarklord.coolcatcanvas.api.client.utils;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.jetbrains.annotations.Nullable;

/**
 * How a rectangular outline looks: a solid color or an animated {@link Rainbow}, its thickness, and how far it sits
 * outside the rectangle. Immutable; the {@code with...} methods return copies.
 * <p>
 * Draw it yourself with {@link #draw}, or attach it to a widget with
 * {@link net.ixdarklord.coolcatcanvas.api.client.gui.components.ElementOutlines ElementOutlines}.
 * <p>
 * For a rainbow outline the {@link Rainbow#spread() spread} is measured around the outline: 1 puts one full rainbow
 * around it whatever its size, 2 puts two. The colors run clockwise from the top-left corner.
 */
public final class Outline {
    private final int color;
    private final @Nullable Rainbow rainbow;
    private final int thickness;
    private final int padding;
    private final int segment;

    private Outline(int color, @Nullable Rainbow rainbow, int thickness, int padding, int segment) {
        this.color = color;
        this.rainbow = rainbow;
        this.thickness = Math.max(1, thickness);
        this.padding = padding;
        this.segment = Math.max(1, segment);
    }

    /** A 1 pixel outline in one ARGB color. */
    public static Outline solid(int color) {
        return new Outline(RenderUtils.textColor(color), null, 1, 0, 2);
    }

    /** A 1 pixel outline with one full, animated rainbow around it. */
    public static Outline rainbow() {
        return rainbow(Rainbow.DEFAULT.withSpread(1.0F));
    }

    public static Outline rainbow(Rainbow rainbow) {
        return new Outline(0xFFFFFFFF, rainbow, 1, 0, 2);
    }

    /** Line width in pixels, at least 1. */
    public Outline withThickness(int thickness) {
        return new Outline(this.color, this.rainbow, thickness, this.padding, this.segment);
    }

    /** Pixels between the rectangle and the outline; negative draws inside it. */
    public Outline withPadding(int padding) {
        return new Outline(this.color, this.rainbow, this.thickness, padding, this.segment);
    }

    /**
     * Length in pixels of each single-colored piece of a rainbow outline (2 by default). Longer pieces are fewer
     * draws but show visible steps.
     */
    public Outline withSegmentLength(int segment) {
        return new Outline(this.color, this.rainbow, this.thickness, this.padding, segment);
    }

    /** A solid outline's color, or the alpha multiplier (its alpha byte) for a rainbow outline. */
    public Outline withColor(int color) {
        return new Outline(RenderUtils.textColor(color), this.rainbow, this.thickness, this.padding, this.segment);
    }

    public int color() {
        return this.color;
    }

    /** The rainbow, or null for a solid outline. */
    public @Nullable Rainbow getRainbow() {
        return this.rainbow;
    }

    public int thickness() {
        return this.thickness;
    }

    public int padding() {
        return this.padding;
    }

    public int segmentLength() {
        return this.segment;
    }

    // ------------------------------------------------------------------------
    // DRAWING
    // ------------------------------------------------------------------------

    public void draw(GuiGraphicsExtractor graphics, ScreenRectangle rectangle) {
        this.draw(graphics, rectangle.left(), rectangle.top(), rectangle.width(), rectangle.height());
    }

    /** Draws around the rectangle at ({@code x}, {@code y}) of the given size, grown by the padding. */
    public void draw(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        int x0 = x - this.padding;
        int y0 = y - this.padding;
        int w = width + this.padding * 2;
        int h = height + this.padding * 2;
        if (w <= 0 || h <= 0) return;

        int t = Math.min(this.thickness, Math.min((w + 1) / 2, (h + 1) / 2));
        if (this.rainbow == null) {
            RenderUtils.drawHollowRect(graphics, x0, y0, w, h, t, this.color);
        } else {
            this.drawRainbow(graphics, x0, y0, w, h, t);
        }
    }

    // Walks the outline clockwise from the top-left corner; each segment is colored by how far around it starts.
    // The corners belong to the horizontal edges, so nothing is drawn twice.
    private void drawRainbow(GuiGraphicsExtractor graphics, int x0, int y0, int w, int h, int t) {
        Rainbow rainbow = this.rainbow;
        int alpha = this.color >>> 24;
        int x1 = x0 + w;
        int y1 = y0 + h;
        float perimeter = 2.0F * (w + h);
        int s = this.segment;

        // Top, left to right.
        for (int i = 0; i < w; i += s) {
            int color = rainbow.color(i / perimeter, alpha);
            graphics.fill(x0 + i, y0, Math.min(x0 + i + s, x1), y0 + t, color);
        }
        // Right, top to bottom.
        for (int i = t; i < h - t; i += s) {
            int color = rainbow.color((w + i) / perimeter, alpha);
            graphics.fill(x1 - t, y0 + i, x1, Math.min(y0 + i + s, y1 - t), color);
        }
        // Bottom, right to left.
        for (int i = 0; i < w; i += s) {
            int color = rainbow.color((w + h + i) / perimeter, alpha);
            graphics.fill(Math.max(x1 - i - s, x0), y1 - t, x1 - i, y1, color);
        }
        // Left, bottom to top.
        for (int i = t; i < h - t; i += s) {
            int color = rainbow.color((2 * w + h + i) / perimeter, alpha);
            graphics.fill(x0, Math.max(y1 - i - s, y0 + t), x0 + t, y1 - i, color);
        }
    }
}
