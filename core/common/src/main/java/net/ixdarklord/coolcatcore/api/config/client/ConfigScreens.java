package net.ixdarklord.coolcatcore.api.config.client;

import net.ixdarklord.coolcatcore.api.config.Config;
import net.ixdarklord.coolcatcore.internal.config.ConfigImpl;
import net.ixdarklord.coolcatcore.internal.config.ConfigManager;
import net.ixdarklord.coolcatcore.internal.config.client.gui.CategoryPopup;
import net.ixdarklord.coolcatcore.internal.config.client.gui.ColorPickerScreen;
import net.ixdarklord.coolcatcore.internal.config.client.gui.ConfigScreen;
import net.ixdarklord.coolcatcore.internal.config.client.gui.ConfigSelectScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * Client only: the generated config screens. On NeoForge every mod with a config gets its screen in the mod list
 * automatically; elsewhere, open it from a button or key with these. {@code /coolcatcore_client config open [mod] [category]}
 * opens them too.
 */
public final class ConfigScreens {
    private ConfigScreens() {}

    /**
     * The screen for a mod's configs: the config itself when there's one, or a choice between them.
     *
     * @return null when the mod registered no config
     */
    public static @Nullable Screen create(@Nullable Screen parent, String modId) {
        List<ConfigImpl> configs = ConfigManager.forMod(modId);
        if (configs.isEmpty()) return null;
        if (configs.size() == 1) return create(parent, configs.getFirst());
        return new ConfigSelectScreen(parent, modId);
    }

    /** The screen editing one config. */
    public static Screen create(@Nullable Screen parent, Config config) {
        return ConfigScreen.create(parent, (ConfigImpl) config);
    }

    /**
     * A small window with one category of a config: a group's settings, like the full screen's, with Save and Cancel.
     * It floats over {@code parent} (or over the game when null). The path is the group's keys joined by {@code /} or
     * {@code .}, e.g. {@code "rendering/particles"}; a single setting's path shows just that setting, and an empty path
     * the whole config.
     *
     * @throws IllegalArgumentException when the config has nothing at the path
     */
    public static Screen categoryPopup(@Nullable Screen parent, Config config, String path) {
        return CategoryPopup.create(parent, (ConfigImpl) config, path);
    }

    /**
     * {@link #categoryPopup(Screen, Config, String)} for a mod's config named by the path's first part, e.g.
     * {@code "client/example_category"} for the {@code example_category} group of the mod's {@code client} config.
     *
     * @return null when the mod has no such config or category
     */
    public static @Nullable Screen categoryPopup(@Nullable Screen parent, String modId, String path) {
        return CategoryPopup.create(parent, modId, path);
    }

    /** Opens {@link #categoryPopup(Screen, String, String)} over the current screen, or over the game. */
    public static void openCategory(String modId, String path) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = categoryPopup(minecraft.screen, modId, path);
        if (screen != null) minecraft.setScreen(screen);
    }

    /** A screen listing every mod's configs. */
    public static Screen createModList(@Nullable Screen parent) {
        return new ConfigSelectScreen(parent, null);
    }

    /** Opens a mod's config screen on top of the current screen. */
    public static void open(String modId) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = create(minecraft.screen, modId);
        if (screen != null) minecraft.setScreen(screen);
    }

    /**
     * The color picker config screens use for colors, for any color a mod lets players choose. "Done" passes the
     * picked color (ARGB; opaque unless {@code alpha}) to {@code onDone}, then returns to {@code parent}.
     */
    public static Screen colorPicker(@Nullable Screen parent, Component title, int color, boolean alpha, IntConsumer onDone) {
        return new ColorPickerScreen(parent, title, color, alpha, onDone);
    }

    public static boolean hasConfigs(String modId) {
        return !ConfigManager.forMod(modId).isEmpty();
    }
}
