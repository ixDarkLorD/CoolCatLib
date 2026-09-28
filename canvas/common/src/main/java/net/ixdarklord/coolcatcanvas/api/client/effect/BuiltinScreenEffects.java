package net.ixdarklord.coolcatcanvas.api.client.effect;

import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.resources.ResourceLocation;

/**
 * Ready-made effect definitions shipped with CoolCatLib: Canvas, to {@linkplain ScreenEffects#register register} as they
 * are. They all scale by the effect's strength, so they fade without {@link ScreenEffect#autoBlend}, and stack: two
 * effects registered from the same definition keep their own uniforms.
 * <p>
 * Each lists the uniforms it takes, with their defaults.
 */
public final class BuiltinScreenEffects {
    /**
     * Drains the color out.
     * <ul>
     *     <li>{@code Amount} float 1.0: how much color is removed</li>
     *     <li>{@code Contrast} float 0.0: pushes brights up and darks down, 0 for none</li>
     *     <li>{@code Brightness} float 1.0: multiplies the result</li>
     * </ul>
     */
    public static final ResourceLocation DESATURATE = id("desaturate");

    /**
     * Splits the red and blue channels apart, more towards the edges.
     * <ul>
     *     <li>{@code Amount} float 0.01: the split, in screen widths</li>
     *     <li>{@code PulseSpeed} float 0.0: how fast the split breathes, 0 for steady</li>
     *     <li>{@code Radial} float 1.0: 0 splits evenly, 1 only at the edges</li>
     * </ul>
     */
    public static final ResourceLocation CHROMATIC_ABERRATION = id("chromatic_aberration");

    /**
     * Darkens (or colors) the edges.
     * <ul>
     *     <li>{@code Color} vec4 (0, 0, 0, 1): the edge color, alpha its opacity</li>
     *     <li>{@code Radius} float 0.75: where the fade reaches full, from the center to a corner</li>
     *     <li>{@code Softness} float 0.45: how wide the fade is</li>
     * </ul>
     */
    public static final ResourceLocation VIGNETTE = id("vignette");

    /**
     * Makes the screen ripple, as if seen through heat or water.
     * <ul>
     *     <li>{@code Amplitude} float 0.006: the offset, in screen sizes</li>
     *     <li>{@code Frequency} float 12.0: ripples across the screen</li>
     *     <li>{@code Speed} float 2.0: how fast they move</li>
     * </ul>
     */
    public static final ResourceLocation WOBBLE = id("wobble");

    /**
     * Animated noise over the image.
     * <ul>
     *     <li>{@code Amount} float 0.12: the noise's strength</li>
     *     <li>{@code Size} float 1.5: grain size in pixels</li>
     * </ul>
     */
    public static final ResourceLocation FILM_GRAIN = id("film_grain");

    /**
     * Washes the screen with a color.
     * <ul>
     *     <li>{@code Color} vec4 (1, 0, 0, 0.35): the color, alpha its opacity</li>
     * </ul>
     */
    public static final ResourceLocation TINT = id("tint");

    /**
     * A two-pass box blur.
     * <ul>
     *     <li>{@code Radius} float 6.0: in pixels</li>
     * </ul>
     */
    public static final ResourceLocation BLUR = id("blur");

    /**
     * Leaves fading trails behind anything that moves, from a copy of the previous frames.
     * <ul>
     *     <li>{@code Decay} float 0.8: how long trails last, from 0 (none) to below 1</li>
     * </ul>
     */
    public static final ResourceLocation AFTERIMAGE = id("afterimage");

    /**
     * A heartbeat: the screen throbs and its edges darken on every beat.
     * <ul>
     *     <li>{@code Rate} float 1.2: beats per second</li>
     *     <li>{@code Zoom} float 0.015: how far each beat zooms in</li>
     *     <li>{@code Color} vec4 (0.35, 0, 0, 0.6): the edge color on a beat, alpha its opacity</li>
     * </ul>
     */
    public static final ResourceLocation HEARTBEAT = id("heartbeat");

    private BuiltinScreenEffects() {}

    private static ResourceLocation id(String path) {
        return CoolCatCanvas.rl(path);
    }
}
