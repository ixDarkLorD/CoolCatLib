package net.ixdarklord.coolcatcore.internal.integration.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.ixdarklord.coolcatcore.api.config.client.ConfigScreens;
import net.ixdarklord.coolcatcore.internal.config.ConfigManager;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;

import java.util.HashMap;
import java.util.Map;

// Mod Menu's config button for every mod with a CoolCatLib: Core config (keyed by the config's namespace, which should be
// the mod id). Only loaded when Mod Menu is installed.
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // CoolCatLib: Core's own settings, like every other mod's button.
        return parent -> ConfigScreens.create(parent, CoolCatCore.MOD_ID);
    }

    @Override
    public Map<String, ConfigScreenFactory<?>> getProvidedConfigScreenFactories() {
        Map<String, ConfigScreenFactory<?>> factories = new HashMap<>();
        for (String modId : ConfigManager.modIds()) {
            factories.put(modId, parent -> ConfigScreens.create(parent, modId));
        }
        return factories;
    }
}
