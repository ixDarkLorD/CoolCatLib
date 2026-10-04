package net.ixdarklord.coolcatcore.internal.integration.configured;

import com.mrcrayfish.configured.api.IModConfig;
import com.mrcrayfish.configured.api.IModConfigProvider;
import com.mrcrayfish.configured.api.ModContext;
import net.ixdarklord.coolcatcore.api.config.Config;
import net.ixdarklord.coolcatcore.api.platform.Platform;
import net.ixdarklord.coolcatcore.internal.config.ConfigImpl;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Lists every mod's CoolCatLib: Core configs in Configured (MrCrayfish), which then gives those mods a config button in
 * NeoForge's mod list and Mod Menu. Configured finds this class through Core's mod metadata and only loads it itself.
 * <p>
 * Glazed Menu shows these configs in its own screens, so with Glazed Menu installed there's nothing to list here: the mods'
 * buttons open Glazed Menu's screens instead of Configured's.
 */
public final class ConfiguredProvider implements IModConfigProvider {
    private static final String GLAZED = "glazedmenu";

    @Override
    public Set<IModConfig> getConfigurationsForMod(ModContext context) {
        return Platform.isModLoaded(GLAZED) ? Set.of() : configs(context.modId());
    }

    /** A mod's configs as Configured sees them, whether or not Glazed Menu is installed. */
    public static Set<IModConfig> configs(String modId) {
        Set<IModConfig> configs = new LinkedHashSet<>();
        for (Config config : Config.forMod(modId)) {
            configs.add(new ConfiguredConfig((ConfigImpl) config));
        }
        return configs;
    }
}
