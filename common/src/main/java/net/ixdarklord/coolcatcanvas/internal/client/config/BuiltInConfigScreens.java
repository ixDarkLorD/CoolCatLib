package net.ixdarklord.coolcatcanvas.internal.client.config;

import net.ixdarklord.coolcatcore.api.config.Config;
import net.ixdarklord.coolcatcore.api.config.ConfigTheme;
import net.ixdarklord.coolcatcore.api.config.client.ConfigScreens;
import net.ixdarklord.coolcatcore.internal.config.ConfigImpl;
import net.ixdarklord.coolcatcore.internal.config.ConfigManager;
import net.ixdarklord.coolcatcore.internal.config.client.gui.CategoryPopup;
import net.ixdarklord.coolcatcore.internal.config.client.gui.ColorPickerScreen;
import net.ixdarklord.coolcatcore.internal.config.client.gui.ConfigScreen;
import net.ixdarklord.coolcatcore.internal.config.client.gui.ConfigSelectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntConsumer;

// CoolCatLib's own config screens, for when Glazed Menu isn't installed, the same as on 1.21.1 and 1.20.1: a card for
// each of a mod's configs (or every mod's), a config's screen, popups for one category, and the color picker.
public final class BuiltInConfigScreens implements ConfigScreens.Provider {
    @Override
    public @Nullable Screen create(@Nullable Screen parent, String modId) {
        return this.hasConfigs(modId) ? new ConfigSelectScreen(parent, modId) : null;
    }

    @Override
    public @Nullable Screen create(@Nullable Screen parent, Config config) {
        return ConfigScreen.create(parent, (ConfigImpl) config);
    }

    @Override
    public @Nullable Screen categoryPopup(@Nullable Screen parent, Config config, String path, @Nullable ConfigTheme theme) {
        try {
            return CategoryPopup.create(parent, (ConfigImpl) config, path, theme);
        } catch (IllegalArgumentException e) {
            // Nothing at that path.
            return null;
        }
    }

    @Override
    public @Nullable Screen categoryPopup(@Nullable Screen parent, String modId, String path) {
        return CategoryPopup.create(parent, modId, path);
    }

    @Override
    public Screen createModList(@Nullable Screen parent) {
        return new ConfigSelectScreen(parent, null);
    }

    @Override
    public Screen colorPicker(@Nullable Screen parent, Component title, int color, boolean alpha, IntConsumer onDone) {
        return new ColorPickerScreen(parent, title, color, alpha, onDone);
    }

    @Override
    public boolean hasConfigs(String modId) {
        return !ConfigManager.forMod(modId).isEmpty();
    }
}
