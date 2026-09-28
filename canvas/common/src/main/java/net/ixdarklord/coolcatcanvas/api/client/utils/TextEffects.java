package net.ixdarklord.coolcatcanvas.api.client.utils;

import net.ixdarklord.coolcatcanvas.api.utils.ColorGradient;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FastColor;
import net.minecraft.util.FormattedCharSequence;

/**
 * Gradient (rainbow included) and outlined text for GUI drawing.
 * <p>
 * For a component you build yourself, putting the {@link ColorGradient} in its style ({@link ColorGradient#apply}) is
 * enough. {@link #gradient(FormattedCharSequence, ColorGradient)} does the same to text you already have, replacing its
 * colors, and the result can be kept and handed to anything that draws a {@link FormattedCharSequence} (tooltips,
 * {@code graphics.drawString}, {@link RenderUtils#drawScrollingString}...). The effects combine: an outlined rainbow is
 * {@code drawOutlinedText(graphics, font, rainbow(text), x, y, -1, 0xFF000000)}.
 */
public final class TextEffects {
    private static final int[][] OUTLINE_OFFSETS = {
            {-1, -1}, {0, -1}, {1, -1},
            {-1, 0}, {1, 0},
            {-1, 1}, {0, 1}, {1, 1}
    };

    private TextEffects() {}

    // ------------------------------------------------------------------------
    // SEQUENCES
    // ------------------------------------------------------------------------

    /** The text in {@link ColorGradient#RAINBOW}. */
    public static FormattedCharSequence rainbow(Component text) {
        return gradient(text.getVisualOrderText(), ColorGradient.RAINBOW);
    }

    /** The text in {@link ColorGradient#RAINBOW}. */
    public static FormattedCharSequence rainbow(FormattedCharSequence text) {
        return gradient(text, ColorGradient.RAINBOW);
    }

    public static FormattedCharSequence gradient(Component text, ColorGradient gradient) {
        return gradient(text.getVisualOrderText(), gradient);
    }

    /**
     * Colors the text with the gradient, replacing the colors of its style (bold, italic and the rest are kept). It
     * animates each time it's drawn, so it can be kept.
     */
    public static FormattedCharSequence gradient(FormattedCharSequence text, ColorGradient gradient) {
        TextColor color = gradient.textColor();
        return sink -> text.accept((position, style, codepoint) -> sink.accept(position, style.withColor(color), codepoint));
    }

    /** Draws every character in one color, whatever colors its style gives it. */
    public static FormattedCharSequence recolor(FormattedCharSequence text, int color) {
        int rgb = color & 0xFFFFFF;
        return sink -> text.accept((position, style, codepoint) -> sink.accept(position, style.withColor(rgb), codepoint));
    }

    // ------------------------------------------------------------------------
    // GRADIENT TEXT
    // ------------------------------------------------------------------------

    public static void drawGradientText(GuiGraphics graphics, Font font, String text, int x, int y, ColorGradient gradient, boolean shadow) {
        drawGradientText(graphics, font, Component.literal(text).getVisualOrderText(), x, y, gradient, shadow);
    }

    public static void drawGradientText(GuiGraphics graphics, Font font, Component text, int x, int y, ColorGradient gradient, boolean shadow) {
        drawGradientText(graphics, font, text.getVisualOrderText(), x, y, gradient, shadow);
    }

    public static void drawGradientText(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, ColorGradient gradient, boolean shadow) {
        graphics.drawString(font, gradient(text, gradient), x, y, 0xFFFFFFFF, shadow);
    }

    public static void drawCenteredGradientText(GuiGraphics graphics, Font font, Component text, int centerX, int y, ColorGradient gradient, boolean shadow) {
        FormattedCharSequence sequence = text.getVisualOrderText();
        drawGradientText(graphics, font, sequence, centerX - font.width(sequence) / 2, y, gradient, shadow);
    }

    // ------------------------------------------------------------------------
    // OUTLINED TEXT
    // ------------------------------------------------------------------------

    public static void drawOutlinedText(GuiGraphics graphics, Font font, String text, int x, int y, int color, int outlineColor) {
        drawOutlinedText(graphics, font, Component.literal(text).getVisualOrderText(), x, y, color, outlineColor);
    }

    public static void drawOutlinedText(GuiGraphics graphics, Font font, Component text, int x, int y, int color, int outlineColor) {
        drawOutlinedText(graphics, font, text.getVisualOrderText(), x, y, color, outlineColor);
    }

    /**
     * Draws the text with a one pixel outline around every glyph, like glowing signs. {@code color} is the base
     * color (the text's own style colors still apply over it); the outline is {@code outlineColor} everywhere.
     */
    public static void drawOutlinedText(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, int color, int outlineColor) {
        drawOutline(graphics, font, recolor(text, outlineColor), x, y, RenderUtils.textColor(outlineColor));
        graphics.drawString(font, text, x, y, RenderUtils.textColor(color), false);
    }

    /** Draws the text with an animated gradient outline. */
    public static void drawGradientOutlinedText(GuiGraphics graphics, Font font, Component text, int x, int y, int color, ColorGradient outline) {
        drawGradientOutlinedText(graphics, font, text.getVisualOrderText(), x, y, color, outline);
    }

    public static void drawGradientOutlinedText(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, int color, ColorGradient outline) {
        drawOutline(graphics, font, gradient(text, outline), x, y, FastColor.ARGB32.color(FastColor.ARGB32.alpha(RenderUtils.textColor(color)), 0xFFFFFF));
        graphics.drawString(font, text, x, y, RenderUtils.textColor(color), false);
    }

    public static void drawCenteredOutlinedText(GuiGraphics graphics, Font font, Component text, int centerX, int y, int color, int outlineColor) {
        FormattedCharSequence sequence = text.getVisualOrderText();
        drawOutlinedText(graphics, font, sequence, centerX - font.width(sequence) / 2, y, color, outlineColor);
    }

    private static void drawOutline(GuiGraphics graphics, Font font, FormattedCharSequence outline, int x, int y, int color) {
        for (int[] offset : OUTLINE_OFFSETS) {
            graphics.drawString(font, outline, x + offset[0], y + offset[1], color, false);
        }
    }
}
