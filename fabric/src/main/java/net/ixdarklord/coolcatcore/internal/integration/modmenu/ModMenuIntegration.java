package net.ixdarklord.coolcatcore.internal.integration.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;
import net.ixdarklord.coolcatcore.api.config.client.ConfigScreens;
import net.ixdarklord.coolcatcore.internal.config.ConfigManager;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;

import java.util.HashMap;
import java.util.Map;

// Mod Menu's config buttons: CoolCatLib's own, which lists every mod's configs, and one for every mod with a CoolCatLib
// config (keyed by the config's namespace, which should be the mod id). They open Glazed Menu's screens when it's
// installed, CoolCatLib's own otherwise; with Glazed Menu, the other mods' buttons are its to give. Only loaded when Mod
// Menu is installed.
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreens::createModList;
    }

    @Override
    public Map<String, ConfigScreenFactory<?>> getProvidedConfigScreenFactories() {
        Map<String, ConfigScreenFactory<?>> factories = new HashMap<>();
        if (FabricLoader.getInstance().isModLoaded("glazedmenu")) return factories;
        for (String modId : ConfigManager.modIds()) {
            if (!modId.equals(CoolCatCore.MOD_ID)) factories.put(modId, parent -> ConfigScreens.create(parent, modId));
        }
        return factories;
    }
}
