package net.ixdarklord.coolcatlib.api.utils;

import net.minecraft.util.Mth;
import java.awt.*;

/**
 * Utility record representing and manipulating colors in RGBA format.
 * <p>
 * Provides methods for extracting normalized channels, blending, and converting
 * between RGB/RGBA integer and {@link Color} representations.
 */
public record ColorUtils(int rgb) {

    /**
     * Returns the normalized alpha channel (0.0–1.0).
     */
    public float alpha() {
        return ((rgb >> 24) & 0xFF) / 255.0F;
    }

    /**
     * Returns the normalized red channel (0.0–1.0).
     */
    public float red() {
        return ((rgb >> 16) & 0xFF) / 255.0F;
    }

    /**
     * Returns the normalized green channel (0.0–1.0).
     */
    public float green() {
        return ((rgb >> 8) & 0xFF) / 255.0F;
    }

    /**
     * Returns the normalized blue channel (0.0–1.0).
     */
    public float blue() {
        return (rgb & 0xFF) / 255.0F;
    }

    /**
     * Converts this color to a standard {@link Color} instance.
     *
     * @return a {@link Color} based on this {@code rgb} value (including alpha)
     */
    public Color toColor() {
        return new Color(rgb, true);
    }

    /**
     * Creates a {@code ColorUtils} instance from a standard {@link Color}.
     *
     * @param color the {@link Color} to convert
     * @return a new {@code ColorUtils} wrapping the color's RGBA value
     */
    public static ColorUtils of(Color color) {
        return new ColorUtils(color.getRGB());
    }

    /**
     * Converts an RGB integer value to RGBA by applying the given alpha.
     *
     * @param rgb   the RGB color value (without alpha)
     * @param alpha the alpha value (0.0–1.0)
     * @return the RGBA color integer
     */
    public static int rgbToRgba(int rgb, float alpha) {
        int a = (int) (Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    /**
     * Converts a {@link Color} to an RGBA integer by replacing its alpha component.
     *
     * @param color the input {@link Color}
     * @param alpha the new alpha value (0.0–1.0)
     * @return the RGBA color integer with the adjusted alpha
     */
    public static int rgbToRgba(Color color, float alpha) {
        int rgb = color.getRGB() & 0xFFFFFF;
        int a = (int) (Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        return (a << 24) | rgb;
    }

    /**
     * Linearly blends two colors based on the specified ratio.
     * <p>
     * A ratio of {@code 0.0} returns {@code color1}, while a ratio of {@code 1.0} returns {@code color2}.
     * Intermediate values produce a smooth gradient between the two colors.
     * </p>
     *
     * @param color1 the starting color (when {@code ratio = 0.0})
     * @param color2 the ending color (when {@code ratio = 1.0})
     * @param ratio  the blend ratio in the range {@code [0.0, 1.0]}
     * @return a new {@link Color} representing the blended result
     */
    public static Color blend(Color color1, Color color2, double ratio) {
        ratio = Mth.clamp(ratio, 0.0, 1.0);
        int r = (int) Mth.lerp(ratio, color1.getRed(), color2.getRed());
        int g = (int) Mth.lerp(ratio, color1.getGreen(), color2.getGreen());
        int b = (int) Mth.lerp(ratio, color1.getBlue(), color2.getBlue());
        int a = (int) Mth.lerp(ratio, color1.getAlpha(), color2.getAlpha());
        return new Color(r, g, b, a);
    }

    /**
     * Blends smoothly across multiple colors according to a normalized progress value.
     * <p>
     * This method interpolates between consecutive colors in the provided array.
     * For example, with three colors and {@code progress = 0.5}, the result will
     * be approximately halfway between the first and second colors.
     * </p>
     *
     * <p>Behavior:</p>
     * <ul>
     *     <li>{@code progress = 0.0} → returns the first color</li>
     *     <li>{@code progress = 1.0} → returns the last color</li>
     *     <li>Values in between produce smooth transitions</li>
     * </ul>
     *
     * @param progress the normalized blend progress, clamped to {@code [0.0, 1.0]}
     * @param colors   one or more {@link Color} instances to interpolate between
     * @return the blended {@link Color} corresponding to the given progress
     * @throws IllegalArgumentException if {@code colors} is null or empty
     */
    public static Color multiBlend(double progress, Color... colors) {
        if (colors == null || colors.length == 0)
            throw new IllegalArgumentException("At least one color required");
        if (colors.length == 1)
            return colors[0];

        progress = Mth.clamp(progress, 0.0, 1.0);

        double scaled = progress * (colors.length - 1);
        int index = (int) Math.floor(scaled);
        double t = scaled - index;

        Color from = colors[Math.max(0, Math.min(index, colors.length - 1))];
        Color to   = colors[Math.max(0, Math.min(index + 1, colors.length - 1))];

        return blend(from, to, t);
    }

    /**
     * Prints a color's RGBA components to the console for debugging.
     *
     * @param color the color to print
     */
    public static void debugPrint(Color color) {
        System.out.printf("RGBA: %d, %d, %d, %d%n",
                color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }
}
