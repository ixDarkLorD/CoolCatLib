package net.ixdarklord.coolcatcanvas.api.utils;

import net.minecraft.util.Mth;

/**
 * Easing curves, each mapping a progress in {@code [0, 1]} to an eased value (0 at the start, 1 at the end).
 * {@link Easings} names the ones here, for files and commands.
 */
@FunctionalInterface
public interface Easing {
    Easing LINEAR = t -> t;
    Easing STEP = t -> t < 1.0F ? 0.0F : 1.0F;

    Easing SINE_IN = t -> 1.0F - Mth.cos(t * Mth.HALF_PI);
    Easing SINE_OUT = t -> Mth.sin(t * Mth.HALF_PI);
    Easing SINE_IN_OUT = t -> -(Mth.cos(Mth.PI * t) - 1.0F) / 2.0F;

    Easing QUAD_IN = t -> t * t;
    Easing QUAD_OUT = t -> 1.0F - (1.0F - t) * (1.0F - t);
    Easing QUAD_IN_OUT = t -> t < 0.5F ? 2.0F * t * t : 1.0F - (-2.0F * t + 2.0F) * (-2.0F * t + 2.0F) / 2.0F;

    Easing CUBIC_IN = t -> t * t * t;
    Easing CUBIC_OUT = t -> 1.0F - cube(1.0F - t);
    Easing CUBIC_IN_OUT = t -> t < 0.5F ? 4.0F * t * t * t : 1.0F - cube(-2.0F * t + 2.0F) / 2.0F;

    Easing QUART_IN = t -> t * t * t * t;
    Easing QUART_OUT = t -> 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t) * (1.0F - t);
    Easing QUART_IN_OUT = t -> t < 0.5F ? 8.0F * t * t * t * t : 1.0F - 8.0F * (1.0F - t) * (1.0F - t) * (1.0F - t) * (1.0F - t);

    Easing EXPO_IN = t -> t <= 0.0F ? 0.0F : (float) Math.pow(2.0, 10.0 * t - 10.0);
    Easing EXPO_OUT = t -> t >= 1.0F ? 1.0F : 1.0F - (float) Math.pow(2.0, -10.0 * t);
    Easing EXPO_IN_OUT = t -> t <= 0.0F ? 0.0F : t >= 1.0F ? 1.0F
            : t < 0.5F ? (float) Math.pow(2.0, 20.0 * t - 10.0) / 2.0F : (2.0F - (float) Math.pow(2.0, -20.0 * t + 10.0)) / 2.0F;

    /** Hermite smoothstep: a gentle ease in and out, with no acceleration at either end. */
    Easing SMOOTH = t -> t * t * (3.0F - 2.0F * t);

    /** Pulls back a little before setting off. */
    Easing BACK_IN = t -> 2.70158F * t * t * t - 1.70158F * t * t;
    /** Overshoots the end slightly, then settles. */
    Easing BACK_OUT = t -> 1.0F + 2.70158F * cube(t - 1.0F) + 1.70158F * (t - 1.0F) * (t - 1.0F);
    Easing BACK_IN_OUT = t -> t < 0.5F
            ? (2.0F * t) * (2.0F * t) * (3.5949095F * 2.0F * t - 2.5949095F) / 2.0F
            : ((2.0F * t - 2.0F) * (2.0F * t - 2.0F) * (3.5949095F * (2.0F * t - 2.0F) + 2.5949095F) + 2.0F) / 2.0F;

    /** Springs past the end and wobbles into place. */
    Easing ELASTIC_OUT = t -> t <= 0.0F ? 0.0F : t >= 1.0F ? 1.0F
            : (float) Math.pow(2.0, -10.0 * t) * Mth.sin((t * 10.0F - 0.75F) * (Mth.TWO_PI / 3.0F)) + 1.0F;
    /** Lands and bounces, each bounce smaller. */
    Easing BOUNCE_OUT = t -> {
        if (t < 1.0F / 2.75F) return 7.5625F * t * t;
        if (t < 2.0F / 2.75F) return 7.5625F * (t -= 1.5F / 2.75F) * t + 0.75F;
        if (t < 2.5F / 2.75F) return 7.5625F * (t -= 2.25F / 2.75F) * t + 0.9375F;
        return 7.5625F * (t -= 2.625F / 2.75F) * t + 0.984375F;
    };

    /** Eases a progress in {@code [0, 1]}. */
    float apply(float progress);

    /** This curve clamped: progress outside {@code [0, 1]} is pinned to its ends. */
    default float applyClamped(float progress) {
        return this.apply(Mth.clamp(progress, 0.0F, 1.0F));
    }

    /** This curve played backwards: starts at the end and eases towards the start. */
    default Easing reversed() {
        return t -> 1.0F - this.apply(1.0F - t);
    }

    private static float cube(float value) {
        return value * value * value;
    }
}
