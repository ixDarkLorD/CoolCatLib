package net.ixdarklord.coolcatcanvas.api.client.vfx;

import net.ixdarklord.coolcatcanvas.api.utils.Easing;
import net.minecraft.util.Mth;

/**
 * A value over a lifetime, in three points: it eases from {@code start} to {@code middle} over the first half, then
 * from {@code middle} to {@code end} over the second. Particles use one for their size, opacity and spin, which is what
 * lets them swell and fade instead of popping in and out.
 * <pre>{@code
 * Curve.constant(0.25F)                       // always 0.25
 * Curve.of(0.0F, 1.0F)                        // grows, linearly
 * Curve.of(0.0F, 1.0F, 0.0F)                  // in, then out again
 * Curve.of(0.2F, 0.6F, 0.0F).easing(Easing.EXPO_OUT, Easing.SINE_IN)   // bursts open, then drifts shut
 * }</pre>
 *
 * @param in  the easing of the first half
 * @param out the easing of the second half
 */
public record Curve(float start, float middle, float end, Easing in, Easing out) {
    public static final Curve ZERO = constant(0.0F);
    public static final Curve ONE = constant(1.0F);
    /** Opacity for most particles: quickly in, slowly out. */
    public static final Curve FADE = new Curve(0.0F, 1.0F, 0.0F, Easing.EXPO_OUT, Easing.SINE_IN_OUT);

    public static Curve constant(float value) {
        return new Curve(value, value, value, Easing.LINEAR, Easing.LINEAR);
    }

    /** From {@code start} to {@code end}, passing half way between them at half the lifetime. */
    public static Curve of(float start, float end) {
        return new Curve(start, (start + end) * 0.5F, end, Easing.LINEAR, Easing.LINEAR);
    }

    public static Curve of(float start, float middle, float end) {
        return new Curve(start, middle, end, Easing.LINEAR, Easing.LINEAR);
    }

    /** This curve with both halves eased the same way. */
    public Curve easing(Easing easing) {
        return new Curve(this.start, this.middle, this.end, easing, easing);
    }

    public Curve easing(Easing in, Easing out) {
        return new Curve(this.start, this.middle, this.end, in, out);
    }

    /** This curve with every point multiplied. */
    public Curve scaled(float factor) {
        return new Curve(this.start * factor, this.middle * factor, this.end * factor, this.in, this.out);
    }

    /** The value at a progress through the lifetime, {@code 0} to {@code 1} (clamped). */
    public float at(float progress) {
        float t = Mth.clamp(progress, 0.0F, 1.0F);
        return t < 0.5F
                ? Mth.lerp(this.in.apply(t * 2.0F), this.start, this.middle)
                : Mth.lerp(this.out.apply((t - 0.5F) * 2.0F), this.middle, this.end);
    }
}
