package net.ixdarklord.coolcatcanvas.internal.client.gui.style;

import net.ixdarklord.coolcatcanvas.internal.client.effect.ScreenEffectPreferences;
import net.ixdarklord.coolcatcore.api.config.ConfigTheme;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Minecraft;

// How Canvas's styled screens (the screen effects screen) look: dark or light as the player switched it (saved with the
// effects screen's choices), themes' own opacities, and animations unless the Fast graphics preset is on.
public final class StyleSettings {
    private StyleSettings() {}

    public static ConfigTheme.Mode themeMode() {
        return ScreenEffectPreferences.lightMode() ? ConfigTheme.Mode.LIGHT : ConfigTheme.Mode.DARK;
    }

    /** Switches between dark and light, and saves the choice. */
    public static void toggleThemeMode() {
        ScreenEffectPreferences.setLightMode(!ScreenEffectPreferences.lightMode());
    }

    /** A theme's backdrop opacity. */
    public static float backgroundOpacity(float themeOpacity) {
        return themeOpacity;
    }

    /** A theme's texture opacity. */
    public static float textureOpacity(float themeOpacity) {
        return themeOpacity;
    }

    /** Whether the screens draw their themes' effects: never on Fast graphics. */
    public static boolean themeEffects() {
        return !fastGraphics();
    }

    /** Whether pages animate into place: never on Fast graphics. */
    public static boolean transitions() {
        return !fastGraphics();
    }

    // The Fast graphics preset asks for the cheapest look, so it turns the screens' animations off.
    private static boolean fastGraphics() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.options.graphicsPreset().get() == GraphicsPreset.FAST;
    }
}
