package net.ixdarklord.coolcatcore.internal.client.gui.style;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.ixdarklord.coolcatcore.api.config.ConfigTheme;
import net.ixdarklord.coolcatcore.api.platform.Platform;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

// How the styled screens (the config screens, and screens of mods drawn in their style) look: dark or light as the
// player switched it (saved in config/coolcatlib-style.json), themes' own opacities, and animations unless the Fast
// graphics preset is on.
public final class StyleSettings {
    private static boolean lightMode;
    private static boolean loaded;

    private StyleSettings() {}

    public static ConfigTheme.Mode themeMode() {
        return lightMode() ? ConfigTheme.Mode.LIGHT : ConfigTheme.Mode.DARK;
    }

    /** Switches between dark and light, and saves the choice. */
    public static synchronized void toggleThemeMode() {
        lightMode = !lightMode();
        JsonObject json = new JsonObject();
        json.addProperty("lightMode", lightMode);
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, json.toString());
        } catch (IOException e) {
            CoolCatCore.LOGGER.warn("Couldn't save {}", file, e);
        }
    }

    private static synchronized boolean lightMode() {
        if (!loaded) {
            loaded = true;
            Path file = file();
            // Before it had a file of its own, the choice was kept with the screen effects screen's.
            Path old = Platform.getConfigFolder().resolve("coolcatlib-screen-effects.json");
            lightMode = readLightMode(Files.exists(file) ? file : old);
        }
        return lightMode;
    }

    private static boolean readLightMode(Path file) {
        if (!Files.exists(file)) return false;
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            return json.has("lightMode") && json.get("lightMode").getAsBoolean();
        } catch (IOException | RuntimeException e) {
            CoolCatCore.LOGGER.warn("Couldn't read {}", file, e);
            return false;
        }
    }

    private static Path file() {
        return Platform.getConfigFolder().resolve("coolcatlib-style.json");
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
