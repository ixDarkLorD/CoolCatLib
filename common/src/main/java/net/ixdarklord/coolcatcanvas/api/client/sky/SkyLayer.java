package net.ixdarklord.coolcatcanvas.api.client.sky;

import net.ixdarklord.coolcatcanvas.api.utils.Easing;

/**
 * Runtime control over one layer of a skybox, on top of its definition. Found by name with {@link Skybox#layer}; the
 * handle stays valid across resource reloads, and a name the definition lacks is simply never drawn.
 * <p>
 * Durations are in ticks of real time (20 per second). Only use a layer from the render thread.
 */
public interface SkyLayer {
    String name();

    /** Shows or hides the layer at once. Layers start visible. */
    SkyLayer setVisible(boolean visible);

    boolean isVisible();

    // ---- Opacity ----

    /** Multiplies the layer's opacity, clamped to {@code [0, 1]}. Stops any opacity animation. */
    SkyLayer setAlpha(float alpha);

    /** Moves the opacity multiplier to {@code target} over {@code ticks}. */
    SkyLayer animateAlpha(float target, int ticks, Easing easing);

    default SkyLayer fadeIn(int ticks) {
        return this.animateAlpha(1.0F, ticks, Easing.SINE_IN_OUT);
    }

    default SkyLayer fadeOut(int ticks) {
        return this.animateAlpha(0.0F, ticks, Easing.SINE_IN_OUT);
    }

    /** The opacity multiplier set or animated here, 1 unless changed. */
    float alpha();

    // ---- Tint ----

    /** Replaces the definition's tint with a packed ARGB color, its alpha the base opacity. Stops any tint animation. */
    SkyLayer setTint(int argb);

    /** Moves the tint to a packed ARGB color over {@code ticks}. */
    SkyLayer animateTint(int argb, int ticks, Easing easing);

    /** Goes back to the definition's tint. */
    SkyLayer resetTint();

    // ---- Params ----

    /** Sets {@code SkyParams[index]} (0 to 3); missing components are 0. Stops any animation of it. */
    SkyLayer setParam(int index, float... values);

    /** Sets {@code SkyParams[index]} to a packed ARGB color, as RGBA. */
    default SkyLayer setParamColor(int index, int argb) {
        return this.setParam(index, (argb >> 16 & 0xFF) / 255.0F, (argb >> 8 & 0xFF) / 255.0F, (argb & 0xFF) / 255.0F, (argb >>> 24) / 255.0F);
    }

    /** Moves {@code SkyParams[index]} to {@code target} over {@code ticks}. */
    SkyLayer animateParam(int index, int ticks, Easing easing, float... target);

    /** Computes {@code SkyParams[index]} every frame, overriding set and animated values until {@link #resetParams}. */
    SkyLayer bindParam(int index, ParamBinding binding);

    /** Goes back to the definition's params, dropping bindings and animations. */
    SkyLayer resetParams();

    /** The current value of {@code SkyParams[index]}, as 4 floats. */
    float[] param(int index);

    @FunctionalInterface
    interface ParamBinding {
        /** Writes up to 4 components into {@code out}, which starts zeroed. */
        void get(SkyContext context, float[] out);
    }
}
