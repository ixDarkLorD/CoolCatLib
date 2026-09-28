package net.ixdarklord.coolcatcanvas.api.utils;

import net.ixdarklord.coolcatcanvas.internal.text.GradientTextColors;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.ixdarklord.coolcatcore.api.utils.ARGB;
import net.minecraft.Util;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;

/**
 * An animated color gradient: color stops placed evenly around a loop (the last blends back into the first, so it
 * never jumps), or the full {@linkplain #rainbow() rainbow} of hues. The loop scrolls over time ({@link #speed()}) and
 * along a position ({@link #spread()}): a character's index in the text, or the distance around an
 * {@link net.ixdarklord.coolcatcanvas.api.client.utils.Outline Outline}.
 * <pre>{@code
 * ColorGradient.of(0xFF4000, 0xFFD000)          // fire: orange to yellow and back, each letter its own color
 * ColorGradient.RAINBOW.wholeText()             // the whole text in one color, cycling through the rainbow
 * ColorGradient.of(0x40C0FF, 0xFFFFFF).withSpeed(0).perLetter(10)  // a still gradient, repeating every 10 letters
 * }</pre>
 * Components can use one as their color: {@link #apply(MutableComponent)}, {@link #literal(String)} or a style with
 * {@link #textColor()} make the text animate wherever it's drawn (chat, tooltips, item names, signs, widget labels).
 * The color is saved and sent by name, so it also works in JSON text and commands:
 * <ul>
 *     <li>{@code coolcatcanvas:rainbow} ({@link #RAINBOW}),
 *     {@code coolcatcanvas:rainbow_whole} ({@link #RAINBOW_WHOLE})</li>
 *     <li>{@code coolcatcanvas:rainbow/<speed>/<spread>/<saturation>/<brightness>}, trailing values optional</li>
 *     <li>{@code coolcatcanvas:gradient/<speed>/<spread>/#RRGGBB/#RRGGBB...}</li>
 * </ul>
 * e.g. {@code /tellraw @a {"text":"Hi","color":"coolcatcanvas:gradient/0.5/0.05/#FF4000/#FFD000"}}. Players
 * without CoolCatLib: Canvas can't read these colors, so only send them to players who have it.
 * <p>
 * Stop colors are RGB; their alpha is ignored (text and outlines bring their own).
 */
public final class ColorGradient {
    public static final float DEFAULT_SPEED = 0.5F;
    public static final float DEFAULT_SPREAD = 0.05F;

    /** Every hue, each letter its own color, a full rainbow every 20 letters, flowing at half a cycle per second. */
    public static final ColorGradient RAINBOW = rainbow(0.8F, 1.0F);
    /** The whole text in one color, cycling through the rainbow at half a cycle per second. */
    public static final ColorGradient RAINBOW_WHOLE = RAINBOW.wholeText();
    /** A softer rainbow. */
    public static final ColorGradient PASTEL_RAINBOW = rainbow(0.45F, 1.0F);
    /** Red, orange and yellow. */
    public static final ColorGradient FIRE = of(0xFF2A00, 0xFF8C00, 0xFFE040);
    /** Deep blue to cyan. */
    public static final ColorGradient OCEAN = of(0x1040FF, 0x20C0FF, 0x80FFF0);
    /** Green, teal and violet. */
    public static final ColorGradient AURORA = of(0x30FF90, 0x20D0D0, 0x9060FF);

    private final int @Nullable [] colors;
    private final float saturation;
    private final float brightness;
    private final float speed;
    private final float spread;

    private ColorGradient(int @Nullable [] colors, float saturation, float brightness, float speed, float spread) {
        this.colors = colors;
        this.saturation = Mth.clamp(saturation, 0.0F, 1.0F);
        this.brightness = Mth.clamp(brightness, 0.0F, 1.0F);
        this.speed = speed;
        this.spread = spread;
    }

    /**
     * A gradient through the given RGB colors, looping back to the first. One color is a solid color; to fade
     * between two colors without looping through them twice, just give the two.
     */
    public static ColorGradient of(int... colors) {
        if (colors.length == 0) throw new IllegalArgumentException("A gradient needs at least one color");
        int[] rgb = new int[colors.length];
        for (int i = 0; i < colors.length; i++) rgb[i] = colors[i] & 0xFFFFFF;
        return new ColorGradient(rgb, 1.0F, 1.0F, DEFAULT_SPEED, DEFAULT_SPREAD);
    }

    /** Every hue, like {@link #RAINBOW}. */
    public static ColorGradient rainbow() {
        return RAINBOW;
    }

    /** Every hue at the given saturation (0 white to 1 full color) and brightness (0 black to 1 full). */
    public static ColorGradient rainbow(float saturation, float brightness) {
        return new ColorGradient(null, saturation, brightness, DEFAULT_SPEED, DEFAULT_SPREAD);
    }

    // ------------------------------------------------------------------------
    // OPTIONS
    // ------------------------------------------------------------------------

    /** Full loops per second; negative runs backwards, 0 stands still. */
    public ColorGradient withSpeed(float speed) {
        return new ColorGradient(this.colors, this.saturation, this.brightness, speed, this.spread);
    }

    /** How far along the loop each step of position moves, in loops (0.05 = the whole gradient every 20 letters). */
    public ColorGradient withSpread(float spread) {
        return new ColorGradient(this.colors, this.saturation, this.brightness, this.speed, spread);
    }

    /** Each letter its own color, the whole gradient every {@code letters} letters. */
    public ColorGradient perLetter(int letters) {
        return this.withSpread(1.0F / Math.max(1, letters));
    }

    /** The whole text in one color, cycling through the gradient (a spread of 0). */
    public ColorGradient wholeText() {
        return this.withSpread(0.0F);
    }

    /** For a rainbow: its saturation, 0 (white) to 1 (full color). No effect on a gradient of colors. */
    public ColorGradient withSaturation(float saturation) {
        return new ColorGradient(this.colors, saturation, this.brightness, this.speed, this.spread);
    }

    /** For a rainbow: its brightness, 0 (black) to 1 (full). No effect on a gradient of colors. */
    public ColorGradient withBrightness(float brightness) {
        return new ColorGradient(this.colors, this.saturation, brightness, this.speed, this.spread);
    }

    public float speed() {
        return this.speed;
    }

    public float spread() {
        return this.spread;
    }

    public float saturation() {
        return this.saturation;
    }

    public float brightness() {
        return this.brightness;
    }

    public boolean isRainbow() {
        return this.colors == null;
    }

    public boolean isWholeText() {
        return this.spread == 0.0F;
    }

    /** The RGB color stops, or an empty array for a rainbow. */
    public int[] colors() {
        return this.colors == null ? new int[0] : this.colors.clone();
    }

    // ------------------------------------------------------------------------
    // COLORS
    // ------------------------------------------------------------------------

    /** How far along the loop (0-1) a position is, right now. */
    public float progress(float position) {
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
        return ARGB.color(alpha, this.sample(this.progress(position)));
    }

    /** The RGB color at a point of the loop (0-1), ignoring time and spread. */
    public int sample(float progress) {
        float t = Mth.frac(progress);
        if (this.colors == null) {
            return Mth.hsvToRgb(t, this.saturation, this.brightness) & 0xFFFFFF;
        }
        int count = this.colors.length;
        if (count == 1) return this.colors[0];
        float scaled = t * count;
        int index = Math.min((int) scaled, count - 1);
        return ARGB.srgbLerp(scaled - index, this.colors[index], this.colors[(index + 1) % count]) & 0xFFFFFF;
    }

    // ------------------------------------------------------------------------
    // COMPONENTS
    // ------------------------------------------------------------------------

    /** A text color that draws as this gradient, one step per character. */
    public TextColor textColor() {
        return GradientTextColors.of(this);
    }

    /** The style with this gradient as its color. */
    public Style applyTo(Style style) {
        return style.withColor(this.textColor());
    }

    /** Colors the component (and the children that don't set their own color) with this gradient. */
    public MutableComponent apply(MutableComponent component) {
        return component.withStyle(this::applyTo);
    }

    /** A literal text in this gradient. */
    public MutableComponent literal(String text) {
        return this.apply(Component.literal(text));
    }

    /** A translated text in this gradient. */
    public MutableComponent translatable(String key, Object... args) {
        return this.apply(Component.translatable(key, args));
    }

    /** The gradient a text color draws as, if it's one made by {@link #textColor()}. */
    public static Optional<ColorGradient> fromTextColor(TextColor color) {
        return Optional.ofNullable(GradientTextColors.get(color));
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof ColorGradient other
                && Arrays.equals(this.colors, other.colors)
                && Float.compare(this.saturation, other.saturation) == 0
                && Float.compare(this.brightness, other.brightness) == 0
                && Float.compare(this.speed, other.speed) == 0
                && Float.compare(this.spread, other.spread) == 0;
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(this.colors);
        result = 31 * result + Float.hashCode(this.saturation);
        result = 31 * result + Float.hashCode(this.brightness);
        result = 31 * result + Float.hashCode(this.speed);
        return 31 * result + Float.hashCode(this.spread);
    }

    @Override
    public String toString() {
        return GradientTextColors.name(this);
    }
}
