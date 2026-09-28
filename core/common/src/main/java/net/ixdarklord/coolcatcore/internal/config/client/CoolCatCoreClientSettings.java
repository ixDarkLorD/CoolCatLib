package net.ixdarklord.coolcatcore.internal.config.client;

import net.ixdarklord.coolcatcore.api.config.Config;
import net.ixdarklord.coolcatcore.api.config.ConfigBuilder;
import net.ixdarklord.coolcatcore.api.config.ConfigScope;
import net.ixdarklord.coolcatcore.api.config.ConfigTheme;
import net.ixdarklord.coolcatcore.api.config.ConfigValue;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

// CoolCatLib: Core's own client config: the player's choices for every mod's config screens.
public final class CoolCatCoreClientSettings {
    private static @Nullable ConfigValue<ConfigTheme.Mode> themeMode;
    private static @Nullable ConfigValue<Integer> backgroundOpacity;
    private static @Nullable ConfigValue<Integer> textureOpacity;
    private static @Nullable ConfigValue<Boolean> themeEffects;
    private static @Nullable ConfigValue<Boolean> transitions;

    private CoolCatCoreClientSettings() {}

    static void init() {
        ConfigBuilder builder = Config.builder(CoolCatCore.MOD_ID, ConfigScope.CLIENT).comment("CoolCatLib: Core client settings");
        themeMode = builder.enumValue("themeMode", ConfigTheme.Mode.DARK)
                .comment("Whether config screens use their dark or light colors (the sun/moon button in their top bar).")
                .build();
        builder.push("background", "The config screens' background, for every mod");
        backgroundOpacity = builder.intValue("backgroundOpacity", 100).range(0, 200).slider()
                .comment("Scales how strongly each mod's backdrop color covers the background, in percent: 0 removes it, 200 doubles it.")
                .build();
        textureOpacity = builder.intValue("textureOpacity", 100).range(0, 100).slider()
                .comment("Scales the opacity of each mod's background texture, in percent; below 100 the panorama or world shows through it.")
                .build();
        builder.pop();
        builder.push("effects", "Animations in the config screens, for every mod (always off on the Fast graphics preset)");
        themeEffects = builder.bool("themeEffects", true)
                .comment("Each mod's animated effects: by default soft glows and small falling stars behind the panels, shifting with the mouse.")
                .build();
        transitions = builder.bool("transitions", true)
                .comment("Pages gliding into place when you open a config or go back.")
                .build();
        builder.pop();
        builder.build();
    }

    public static ConfigTheme.Mode themeMode() {
        return themeMode == null ? ConfigTheme.Mode.DARK : themeMode.get();
    }

    /** A theme's backdrop opacity, scaled by the player's choice. */
    public static float backgroundOpacity(float themeOpacity) {
        return scaled(themeOpacity, backgroundOpacity);
    }

    /** A theme's texture opacity, scaled by the player's choice. */
    public static float textureOpacity(float themeOpacity) {
        return scaled(themeOpacity, textureOpacity);
    }

    private static float scaled(float opacity, @Nullable ConfigValue<Integer> percent) {
        return percent == null ? opacity : Math.clamp(opacity * percent.get() / 100.0F, 0, 1);
    }

    /** Whether the config screens draw their themes' effects: the player's choice, but never on Fast graphics. */
    public static boolean themeEffects() {
        return (themeEffects == null || themeEffects.get()) && !fastGraphics();
    }

    /** Whether config pages animate into place: the player's choice, but never on Fast graphics. */
    public static boolean transitions() {
        return (transitions == null || transitions.get()) && !fastGraphics();
    }

    // The Fast graphics preset asks for the cheapest look, so it turns the screens' animations off.
    private static boolean fastGraphics() {
        return Minecraft.getInstance().options.graphicsMode().get() == GraphicsStatus.FAST;
    }

    /** Switches between dark and light, and saves the choice. */
    public static void toggleThemeMode() {
        if (themeMode == null) return;
        themeMode.set(themeMode.get() == ConfigTheme.Mode.DARK ? ConfigTheme.Mode.LIGHT : ConfigTheme.Mode.DARK);
        themeMode.config().save();
    }
}
