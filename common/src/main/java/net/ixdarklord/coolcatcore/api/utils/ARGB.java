package net.ixdarklord.coolcatcore.api.utils;

import net.minecraft.util.Mth;

/**
 * Packed ARGB colour helpers: the parts of newer Minecraft's {@code net.minecraft.util.ARGB} that CoolCatLib uses, for
 * 1.20.1, which only has {@code FastColor.ARGB32}.
 */
public final class ARGB {
    private ARGB() {
    }

    public static int alpha(int color) {
        return color >>> 24;
    }

    public static int red(int color) {
        return color >> 16 & 0xFF;
    }

    public static int green(int color) {
        return color >> 8 & 0xFF;
    }

    public static int blue(int color) {
        return color & 0xFF;
    }

    public static int color(int alpha, int red, int green, int blue) {
        return (alpha & 0xFF) << 24 | (red & 0xFF) << 16 | (green & 0xFF) << 8 | blue & 0xFF;
    }

    public static int color(int red, int green, int blue) {
        return color(0xFF, red, green, blue);
    }

    /** {@code rgb} with the given alpha (0-255). */
    public static int color(int alpha, int rgb) {
        return (alpha & 0xFF) << 24 | rgb & 0xFFFFFF;
    }

    public static int opaque(int color) {
        return color | 0xFF000000;
    }

    public static int transparent(int color) {
        return color & 0xFFFFFF;
    }

    public static float alphaFloat(int color) {
        return alpha(color) / 255.0F;
    }

    public static float redFloat(int color) {
        return red(color) / 255.0F;
    }

    public static float greenFloat(int color) {
        return green(color) / 255.0F;
    }

    public static float blueFloat(int color) {
        return blue(color) / 255.0F;
    }

    public static int as8BitChannel(float value) {
        return Mth.floor(Mth.clamp(value, 0.0F, 1.0F) * 255.0F);
    }

    public static int colorFromFloat(float alpha, float red, float green, float blue) {
        return color(as8BitChannel(alpha), as8BitChannel(red), as8BitChannel(green), as8BitChannel(blue));
    }

    public static int white(float alpha) {
        return color(as8BitChannel(alpha), 0xFF, 0xFF, 0xFF);
    }

    /** Channel by channel product of two colours. */
    public static int multiply(int a, int b) {
        if (a == -1) return b;
        if (b == -1) return a;
        return color(alpha(a) * alpha(b) / 255, red(a) * red(b) / 255, green(a) * green(b) / 255, blue(a) * blue(b) / 255);
    }

    public static int multiplyAlpha(int color, float alphaMultiplier) {
        if (color == 0 || alphaMultiplier <= 0.0F) return 0;
        if (alphaMultiplier >= 1.0F) return color;
        return color(as8BitChannel(alphaFloat(color) * alphaMultiplier), color);
    }

    public static int scaleRGB(int color, float scale) {
        return color(alpha(color), Mth.clamp((int) (red(color) * scale), 0, 255), Mth.clamp((int) (green(color) * scale), 0, 255), Mth.clamp((int) (blue(color) * scale), 0, 255));
    }

    /** Linear interpolation of each channel, from {@code from} (at 0) to {@code to} (at 1). */
    public static int lerp(float delta, int from, int to) {
        return color(Mth.lerpInt(delta, alpha(from), alpha(to)), Mth.lerpInt(delta, red(from), red(to)), Mth.lerpInt(delta, green(from), green(to)), Mth.lerpInt(delta, blue(from), blue(to)));
    }

    /** Interpolation in sRGB space, like newer Minecraft's {@code ARGB.srgbLerp}: each channel, linearly. */
    public static int srgbLerp(float delta, int from, int to) {
        return lerp(delta, from, to);
    }
}
