package net.ixdarklord.coolcatcore.internal.config.client;

import net.minecraft.util.Mth;
import net.ixdarklord.coolcatcore.api.config.Config;
import net.ixdarklord.coolcatcore.api.config.ConfigBuilder;
import net.ixdarklord.coolcatcore.api.config.ConfigScope;
import net.ixdarklord.coolcatcore.api.config.ConfigTheme;
import net.ixdarklord.coolcatcore.api.config.ConfigValue;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import org.jetbrains.annotations.Nullable;

// CoolCatLib: Core's own client config: the player's choices for every mod's config screens.
public final class CoolCatCoreClientSettings {
    private static @Nullable ConfigValue<ConfigTheme.Mode> themeMode;
    private static @Nullable ConfigValue<Integer> backgroundOpacity;
    private static @Nullable ConfigValue<Integer> textureOpacity;

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
        return percent == null ? opacity : Mth.clamp(opacity * percent.get() / 100.0F, 0.0F, 1.0F);
    }

    /** Switches between dark and light, and saves the choice. */
    public static void toggleThemeMode() {
        if (themeMode == null) return;
        themeMode.set(themeMode.get() == ConfigTheme.Mode.DARK ? ConfigTheme.Mode.LIGHT : ConfigTheme.Mode.DARK);
        themeMode.config().save();
    }
}
