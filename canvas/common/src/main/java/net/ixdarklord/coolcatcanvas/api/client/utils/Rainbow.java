package net.ixdarklord.coolcatcanvas.api.client.utils;

import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * An animated rainbow: a hue that cycles over real time and shifts along a position (a character's index in
 * {@link TextEffects}, the distance around an {@link Outline}).
 *
 * @param speed      full hue cycles per second; negative runs backwards, 0 stands still
 * @param spread     hue shift per unit of position, in cycles (0.05 = a full rainbow every 20 characters)
 * @param saturation 0 (white) to 1 (full color)
 * @param brightness 0 (black) to 1 (full brightness)
 */
public record Rainbow(float speed, float spread, float saturation, float brightness) {
    /** Half a cycle per second, a full rainbow every 20 characters. */
    public static final Rainbow DEFAULT = new Rainbow(0.5F, 0.05F, 0.8F, 1.0F);
    /** Softer pastel colors. */
    public static final Rainbow PASTEL = new Rainbow(0.5F, 0.05F, 0.45F, 1.0F);

    public Rainbow {
        saturation = Mth.clamp(saturation, 0.0F, 1.0F);
        brightness = Mth.clamp(brightness, 0.0F, 1.0F);
    }

    public Rainbow withSpeed(float speed) {
        return new Rainbow(speed, this.spread, this.saturation, this.brightness);
    }

    public Rainbow withSpread(float spread) {
        return new Rainbow(this.speed, spread, this.saturation, this.brightness);
    }

    public Rainbow withSaturation(float saturation) {
        return new Rainbow(this.speed, this.spread, saturation, this.brightness);
    }

    public Rainbow withBrightness(float brightness) {
        return new Rainbow(this.speed, this.spread, this.saturation, brightness);
    }

    /** The hue (0-1) at a position, right now. */
    public float hue(float position) {
        // Wrap the clock so the float keeps its precision however long the game has been running.
        float seconds = (Util.getMillis() % 3_600_000L) / 1000.0F;
        return Mth.frac(seconds * this.speed + position * this.spread);
    }

    /** The opaque ARGB color at a position, right now. */
    public int color(float position) {
        return this.color(position, 0xFF);
    }

    /** The ARGB color at a position, right now, with the given alpha (0-255). */
    public int color(float position, int alpha) {
        return Mth.hsvToArgb(this.hue(position), this.saturation, this.brightness, alpha);
    }
}
