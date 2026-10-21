package net.ixdarklord.coolcatcore.api.config.client;

import net.ixdarklord.coolcatcore.api.config.Config;
import net.ixdarklord.coolcatcore.api.config.ConfigScope;
import net.ixdarklord.coolcatcore.api.config.ConfigTheme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.function.IntConsumer;

/**
 * Client only: config screens for Core's configs. They come from Glazed Menu (a separate, client-side mod) when it's
 * installed; without it, CoolCatLib's own simpler screens, in the style of its screen effects screen, stand in. The
 * methods return null only when neither is there (on a dedicated server, or before the client started), and
 * {@link #tellUnavailable(Config)} tells the player how to change a config without screens.
 */
public final class ConfigScreens {
    private static @Nullable Provider provider;
    private static @Nullable Provider fallback;

    private ConfigScreens() {}

    /** What makes the screens: Glazed Menu sets it as it starts, and CoolCatLib's own screens are the fallback. */
    @ApiStatus.Internal
    public interface Provider {
        @Nullable Screen create(@Nullable Screen parent, String modId);

        @Nullable Screen create(@Nullable Screen parent, Config config);

        @Nullable Screen categoryPopup(@Nullable Screen parent, Config config, String path, @Nullable ConfigTheme theme);

        @Nullable Screen categoryPopup(@Nullable Screen parent, String modId, String path);

        Screen createModList(@Nullable Screen parent);

        Screen colorPicker(@Nullable Screen parent, Component title, int color, boolean alpha, IntConsumer onDone);

        boolean hasConfigs(String modId);
    }

    @ApiStatus.Internal
    public static void setProvider(Provider screens) {
        provider = screens;
    }

    /** CoolCatLib's own screens, used while no provider is set. */
    @ApiStatus.Internal
    public static void setFallback(Provider screens) {
        fallback = screens;
    }

    /** Whether there are config screens: Glazed Menu's or CoolCatLib's own. */
    public static boolean isAvailable() {
        return screens() != null;
    }

    /** Whether the screens are Glazed Menu's rather than CoolCatLib's simpler ones. */
    public static boolean isGlazedMenu() {
        return provider != null;
    }

    private static @Nullable Provider screens() {
        return provider != null ? provider : fallback;
    }

    /** The mod's config screen (its configs' list, or the one config), or null when it has none or there are no screens. */
    public static @Nullable Screen create(@Nullable Screen parent, String modId) {
        Provider screens = screens();
        return screens != null ? screens.create(parent, modId) : null;
    }

    /** The screen editing one config, or null without screens. */
    public static @Nullable Screen create(@Nullable Screen parent, Config config) {
        Provider screens = screens();
        return screens != null ? screens.create(parent, config) : null;
    }

    /** A popup editing one category of a config (its path, like {@code "client.visuals"}), or null without screens. */
    public static @Nullable Screen categoryPopup(@Nullable Screen parent, Config config, String path) {
        Provider screens = screens();
        return screens != null ? screens.categoryPopup(parent, config, path, null) : null;
    }

    /** As {@link #categoryPopup(Screen, Config, String)}, in its own theme. */
    public static @Nullable Screen categoryPopup(@Nullable Screen parent, Config config, String path, ConfigTheme theme) {
        Provider screens = screens();
        return screens != null ? screens.categoryPopup(parent, config, path, theme) : null;
    }

    /** A popup editing one category of a mod's configs, or null when it has none there or there are no screens. */
    public static @Nullable Screen categoryPopup(@Nullable Screen parent, String modId, String path) {
        Provider screens = screens();
        return screens != null ? screens.categoryPopup(parent, modId, path) : null;
    }

    /** Opens {@link #categoryPopup(Screen, String, String)} over the current screen; nothing without screens. */
    public static void openCategory(String modId, String path) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = categoryPopup(minecraft.screen, modId, path);
        if (screen != null) minecraft.setScreen(screen);
    }

    /** A screen listing every mod's configs, or null without screens. */
    public static @Nullable Screen createModList(@Nullable Screen parent) {
        Provider screens = screens();
        return screens != null ? screens.createModList(parent) : null;
    }

    /** Opens a mod's config screen on top of the current screen; nothing without screens. */
    public static void open(String modId) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = create(minecraft.screen, modId);
        if (screen != null) minecraft.setScreen(screen);
    }

    /** A color picker in the screens' style, or null without screens. */
    public static @Nullable Screen colorPicker(@Nullable Screen parent, Component title, int color, boolean alpha, IntConsumer onDone) {
        Provider screens = screens();
        return screens != null ? screens.colorPicker(parent, title, color, alpha, onDone) : null;
    }

    /** Whether the mod has configs the screens can show; false without screens. */
    public static boolean hasConfigs(String modId) {
        Provider screens = screens();
        return screens != null && screens.hasConfigs(modId);
    }

    /**
     * Without screens: what to tell the player who wants to change the config in game. Install Glazed Menu, or edit the
     * config's file, which they can click to open its folder.
     */
    public static Component unavailableMessage(Config config) {
        Component file = Component.translatableWithFallback("coolcatlib.screens.unavailable." + fileKind(config.scope()), fileFallback(config.scope()))
                .withStyle(style -> style.withUnderlined(true).withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent.OpenFile(folderOf(config)))
                        .withHoverEvent(new HoverEvent.ShowText(Component.translatableWithFallback("coolcatlib.screens.unavailable.open_folder",
                                "Click to open the config folder"))));
        return Component.translatableWithFallback("coolcatlib.screens.unavailable", "To change this in game, install Glazed Menu, or edit the %s.", file)
                .withStyle(ChatFormatting.GRAY);
    }

    /** Puts {@link #unavailableMessage(Config)} in the player's chat. */
    public static void tellUnavailable(Config config) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gui.getChat().addClientSystemMessage(unavailableMessage(config));
    }

    // The folder holding the config's file; the game's config folder when it has none here.
    private static File folderOf(Config config) {
        Path file = config.filePath();
        if (file != null && file.getParent() != null) return file.getParent().toAbsolutePath().toFile();
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").toAbsolutePath().toFile();
    }

    private static String fileKind(ConfigScope scope) {
        return switch (scope) {
            case CLIENT -> "client_file";
            case SERVER -> "server_file";
            default -> "file";
        };
    }

    private static String fileFallback(ConfigScope scope) {
        return switch (scope) {
            case CLIENT -> "client config file";
            case SERVER -> "server config file";
            default -> "config file";
        };
    }
}
