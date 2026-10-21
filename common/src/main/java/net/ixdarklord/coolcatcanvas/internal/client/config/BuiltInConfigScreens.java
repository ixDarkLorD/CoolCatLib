package net.ixdarklord.coolcatcanvas.internal.client.config;

import net.ixdarklord.coolcatcanvas.internal.client.gui.style.StyledScreen;
import net.ixdarklord.coolcatcore.api.config.Config;
import net.ixdarklord.coolcatcore.api.config.ConfigGroup;
import net.ixdarklord.coolcatcore.api.config.ConfigNode;
import net.ixdarklord.coolcatcore.api.config.ConfigTheme;
import net.ixdarklord.coolcatcore.api.config.client.ConfigScreens;
import net.ixdarklord.coolcatcore.api.platform.Platform;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.IntConsumer;

// CoolCatLib's own config screens, for when Glazed Menu isn't installed: every request opens a StyledConfigScreen (a
// category "popup" opens the config's screen at that category).
public final class BuiltInConfigScreens implements ConfigScreens.Provider {
    @Override
    public @Nullable Screen create(@Nullable Screen parent, String modId) {
        List<Config> configs = shown(Config.forMod(modId));
        if (configs.isEmpty()) return null;
        if (configs.size() == 1) return this.create(parent, configs.getFirst());
        return new StyledConfigScreen(parent, Component.literal(Platform.getModName(modId).orElse(modId)), ConfigTheme.forMod(modId), configs, null);
    }

    @Override
    public @Nullable Screen create(@Nullable Screen parent, Config config) {
        return new StyledConfigScreen(parent, config.title(), config.theme(), List.of(config), null);
    }

    @Override
    public @Nullable Screen categoryPopup(@Nullable Screen parent, Config config, String path, @Nullable ConfigTheme theme) {
        return new StyledConfigScreen(parent, config.title(), theme != null ? theme : config.theme(), List.of(config), path);
    }

    // The mod's config holding that group, the path taken as within one of its configs or led by the config's name.
    @Override
    public @Nullable Screen categoryPopup(@Nullable Screen parent, String modId, String path) {
        for (Config config : shown(Config.forMod(modId))) {
            String inside = path.startsWith(config.name() + ".") ? path.substring(config.name().length() + 1) : path;
            if (group(config.root(), inside) != null) return this.categoryPopup(parent, config, inside, null);
        }
        return null;
    }

    @Override
    public Screen createModList(@Nullable Screen parent) {
        return new StyledConfigScreen(parent, Component.translatableWithFallback("coolcatlib.config.screen.all", "Mod Configs"),
                ConfigTheme.forMod(CoolCatCore.MOD_ID), shown(Config.all()), null);
    }

    @Override
    public Screen colorPicker(@Nullable Screen parent, Component title, int color, boolean alpha, IntConsumer onDone) {
        return new ColorPickerPopup(parent, parent instanceof StyledScreen styled ? styled.theme()
                : ConfigTheme.forMod(CoolCatCore.MOD_ID), title, color, alpha, onDone);
    }

    @Override
    public boolean hasConfigs(String modId) {
        return !shown(Config.forMod(modId)).isEmpty();
    }

    // The configs with anything to show.
    private static List<Config> shown(List<Config> configs) {
        return configs.stream().filter(config -> config.values().anyMatch(value -> !value.isHidden())).toList();
    }

    private static @Nullable ConfigGroup group(ConfigGroup root, String path) {
        ConfigGroup group = root;
        for (String key : path.split("\\.")) {
            ConfigNode child = group.child(key);
            if (!(child instanceof ConfigGroup next)) return null;
            group = next;
        }
        return group;
    }
}
