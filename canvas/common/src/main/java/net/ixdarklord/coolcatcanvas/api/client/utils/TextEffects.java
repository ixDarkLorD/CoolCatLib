package net.ixdarklord.coolcatcanvas.api.client.utils;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;

/**
 * Rainbow and outlined text for GUI drawing.
 * <p>
 * {@link #rainbow(FormattedCharSequence, Rainbow)} returns a sequence that recolors itself each time it's drawn, so
 * it can be kept and handed to anything that draws a {@link FormattedCharSequence} (tooltips, {@code graphics.text},
 * {@link RenderUtils#drawScrollingString}...). The effects combine: an outlined rainbow is
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

    public static FormattedCharSequence rainbow(Component text) {
        return rainbow(text.getVisualOrderText(), Rainbow.DEFAULT);
    }

    public static FormattedCharSequence rainbow(Component text, Rainbow rainbow) {
        return rainbow(text.getVisualOrderText(), rainbow);
    }

    public static FormattedCharSequence rainbow(FormattedCharSequence text) {
        return rainbow(text, Rainbow.DEFAULT);
    }

    /**
     * Colors each character by its index along the text, replacing the colors of its style (bold, italic and the
     * rest are kept). The colors are picked when the sequence is drawn, so a kept sequence stays animated.
     */
    public static FormattedCharSequence rainbow(FormattedCharSequence text, Rainbow rainbow) {
        return sink -> {
            int[] index = {0};
            return text.accept((position, style, codepoint) ->
                    sink.accept(position, style.withColor(rainbow.color(index[0]++) & 0xFFFFFF), codepoint));
        };
    }

    /** Draws every character in one color, whatever colors its style gives it. */
    public static FormattedCharSequence recolor(FormattedCharSequence text, int color) {
        int rgb = color & 0xFFFFFF;
        return sink -> text.accept((position, style, codepoint) -> sink.accept(position, style.withColor(rgb), codepoint));
    }

    // ------------------------------------------------------------------------
    // RAINBOW TEXT
    // ------------------------------------------------------------------------

    public static void drawRainbowText(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, boolean shadow) {
        drawRainbowText(graphics, font, Component.literal(text), x, y, Rainbow.DEFAULT, shadow);
    }

    public static void drawRainbowText(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, boolean shadow) {
        drawRainbowText(graphics, font, text, x, y, Rainbow.DEFAULT, shadow);
    }

    public static void drawRainbowText(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, Rainbow rainbow, boolean shadow) {
        drawRainbowText(graphics, font, text.getVisualOrderText(), x, y, rainbow, shadow);
    }

    public static void drawRainbowText(GuiGraphicsExtractor graphics, Font font, FormattedCharSequence text, int x, int y, Rainbow rainbow, boolean shadow) {
        graphics.text(font, rainbow(text, rainbow), x, y, 0xFFFFFFFF, shadow);
    }

    public static void drawCenteredRainbowText(GuiGraphicsExtractor graphics, Font font, Component text, int centerX, int y, Rainbow rainbow, boolean shadow) {
        FormattedCharSequence sequence = text.getVisualOrderText();
        drawRainbowText(graphics, font, sequence, centerX - font.width(sequence) / 2, y, rainbow, shadow);
    }

    // ------------------------------------------------------------------------
    // OUTLINED TEXT
    // ------------------------------------------------------------------------

    public static void drawOutlinedText(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color, int outlineColor) {
        drawOutlinedText(graphics, font, Component.literal(text).getVisualOrderText(), x, y, color, outlineColor);
    }

    public static void drawOutlinedText(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color, int outlineColor) {
        drawOutlinedText(graphics, font, text.getVisualOrderText(), x, y, color, outlineColor);
    }

    /**
     * Draws the text with a one pixel outline around every glyph, like glowing signs. {@code color} is the base
     * color (the text's own style colors still apply over it); the outline is {@code outlineColor} everywhere.
     */
    public static void drawOutlinedText(GuiGraphicsExtractor graphics, Font font, FormattedCharSequence text, int x, int y, int color, int outlineColor) {
        drawOutline(graphics, font, recolor(text, outlineColor), x, y, RenderUtils.textColor(outlineColor));
        graphics.text(font, text, x, y, RenderUtils.textColor(color), false);
    }

    /** Draws the text with an animated rainbow outline. */
    public static void drawRainbowOutlinedText(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color, Rainbow outline) {
        drawRainbowOutlinedText(graphics, font, text.getVisualOrderText(), x, y, color, outline);
    }

    public static void drawRainbowOutlinedText(GuiGraphicsExtractor graphics, Font font, FormattedCharSequence text, int x, int y, int color, Rainbow outline) {
        drawOutline(graphics, font, rainbow(text, outline), x, y, ARGB.white(ARGB.alpha(RenderUtils.textColor(color))));
        graphics.text(font, text, x, y, RenderUtils.textColor(color), false);
    }

    public static void drawCenteredOutlinedText(GuiGraphicsExtractor graphics, Font font, Component text, int centerX, int y, int color, int outlineColor) {
        FormattedCharSequence sequence = text.getVisualOrderText();
        drawOutlinedText(graphics, font, sequence, centerX - font.width(sequence) / 2, y, color, outlineColor);
    }

    private static void drawOutline(GuiGraphicsExtractor graphics, Font font, FormattedCharSequence outline, int x, int y, int color) {
        for (int[] offset : OUTLINE_OFFSETS) {
            graphics.text(font, outline, x + offset[0], y + offset[1], color, false);
        }
    }
}
