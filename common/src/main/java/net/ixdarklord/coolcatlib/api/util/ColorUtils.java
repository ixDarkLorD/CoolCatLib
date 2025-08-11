package net.ixdarklord.coolcatlib.api.util;

import net.minecraft.util.Mth;

import java.awt.*;

public class ColorUtils {
    private final int rgb;
    public ColorUtils(int rgb) {
        this.rgb = rgb;
    }

    public float getAlpha() {
        return ((rgb >> 24) & 0xff) / 255.0F;
    }

    public float getRed() {
        return ((rgb >> 16) & 0xFF) / 255.0F;
    }

    public float getGreen() {
        return ((rgb >> 8) & 0xFF) / 255.0F;
    }

    public float getBlue() {
        return ((rgb) & 0xFF) / 255.0F;
    }

    public static void printColor(Color color) {
        System.out.printf("RGBA: %s,%s,%s,%s%n", color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }

    public static int RGBToRGBA(int rgb, float alpha) {
        int channel = (int) Mth.lerp(Mth.clamp(alpha, 0.0F, 1.0F), 0, 255);

        // Ensure that the alpha value is within the valid range [0, 255]
        channel = Math.min(255, Math.max(0, channel));

        // Shift the alpha value to the appropriate position (24 bits) and combine it with RGB
        return (channel << 24) | (rgb & 0xFFFFFF);
    }

    public static Color blendColors(Color color1, Color color2, double ratio) {
        int r = (int) (color1.getRed() * ratio + color2.getRed() * (1 - ratio));
        int g = (int) (color1.getGreen() * ratio + color2.getGreen() * (1 - ratio));
        int b = (int) (color1.getBlue() * ratio + color2.getBlue() * (1 - ratio));

        return new Color(r, g, b);
    }
}
