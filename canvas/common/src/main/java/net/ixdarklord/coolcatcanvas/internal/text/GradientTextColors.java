package net.ixdarklord.coolcatcanvas.internal.text;

import net.ixdarklord.coolcatcanvas.api.utils.ColorGradient;
import net.ixdarklord.coolcatcanvas.internal.mixin.TextColorAccessor;
import net.minecraft.network.chat.TextColor;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Gradient text colors: TextColors named "coolcatcanvas:rainbow..." or "coolcatcanvas:gradient/..." that carry their
// ColorGradient (TextColorMixin), which the font reads per character (FontPreparedTextBuilderMixin). One instance per
// gradient; parsed names map back to it.
public final class GradientTextColors {
    private static final String NAMESPACE = "coolcatcanvas:";
    public static final String RAINBOW = NAMESPACE + "rainbow";
    public static final String RAINBOW_WHOLE = RAINBOW + "_whole";
    public static final String GRADIENT = NAMESPACE + "gradient";
    private static final Map<ColorGradient, TextColor> COLORS = new ConcurrentHashMap<>();

    private GradientTextColors() {}

    public static TextColor of(ColorGradient gradient) {
        return COLORS.computeIfAbsent(gradient, key -> {
            // Its plain value (the start of the loop) is what code that doesn't know about gradients sees.
            TextColor color = TextColorAccessor.coolcatcanvas$create(key.sample(0.0F), name(key));
            ((Holder) (Object) color).coolcatcanvas$setGradient(key);
            return color;
        });
    }

    public static @Nullable ColorGradient get(@Nullable TextColor color) {
        return color == null ? null : ((Holder) (Object) color).coolcatcanvas$getGradient();
    }

    public static boolean isGradientName(String name) {
        return name.startsWith(RAINBOW) || name.startsWith(GRADIENT);
    }

    public static @Nullable ColorGradient parse(String name) {
        if (name.equals(RAINBOW)) return ColorGradient.RAINBOW;
        if (name.equals(RAINBOW_WHOLE)) return ColorGradient.RAINBOW_WHOLE;
        try {
            if (name.startsWith(RAINBOW + "/")) {
                // speed/spread/saturation/brightness, trailing values optional
                String[] parts = name.substring(RAINBOW.length() + 1).split("/");
                if (parts.length > 4) return null;
                ColorGradient rainbow = ColorGradient.RAINBOW;
                float[] values = {rainbow.speed(), rainbow.spread(), rainbow.saturation(), rainbow.brightness()};
                for (int i = 0; i < parts.length; i++) values[i] = number(parts[i]);
                return ColorGradient.rainbow(values[2], values[3]).withSpeed(values[0]).withSpread(values[1]);
            }
            if (name.startsWith(GRADIENT + "/")) {
                // speed/spread/#RRGGBB/#RRGGBB...
                String[] parts = name.substring(GRADIENT.length() + 1).split("/");
                if (parts.length < 3) return null;
                int[] colors = new int[parts.length - 2];
                for (int i = 0; i < colors.length; i++) {
                    String hex = parts[i + 2];
                    if (!hex.startsWith("#") || hex.length() != 7) return null;
                    colors[i] = Integer.parseInt(hex.substring(1), 16);
                }
                return ColorGradient.of(colors).withSpeed(number(parts[0])).withSpread(number(parts[1]));
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
    }

    public static String name(ColorGradient gradient) {
        if (gradient.equals(ColorGradient.RAINBOW)) return RAINBOW;
        if (gradient.equals(ColorGradient.RAINBOW_WHOLE)) return RAINBOW_WHOLE;
        StringBuilder name = new StringBuilder(gradient.isRainbow() ? RAINBOW : GRADIENT)
                .append('/').append(format(gradient.speed()))
                .append('/').append(format(gradient.spread()));
        if (gradient.isRainbow()) {
            name.append('/').append(format(gradient.saturation())).append('/').append(format(gradient.brightness()));
        } else {
            for (int color : gradient.colors()) name.append('/').append(String.format(Locale.ROOT, "#%06X", color));
        }
        return name.toString();
    }

    private static float number(String text) {
        float value = Float.parseFloat(text);
        if (!Float.isFinite(value)) throw new NumberFormatException(text);
        return value;
    }

    private static String format(float value) {
        return value == (int) value ? Integer.toString((int) value) : Float.toString(value);
    }

    /** Implemented on TextColor by TextColorMixin. */
    public interface Holder {
        @Nullable ColorGradient coolcatcanvas$getGradient();

        void coolcatcanvas$setGradient(ColorGradient gradient);
    }
}
