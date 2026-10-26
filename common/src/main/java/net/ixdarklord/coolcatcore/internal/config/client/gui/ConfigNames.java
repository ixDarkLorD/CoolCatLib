package net.ixdarklord.coolcatcore.internal.config.client.gui;

import net.ixdarklord.coolcatcore.api.config.Config;
import net.ixdarklord.coolcatcore.api.platform.Platform;
import net.ixdarklord.coolcatcore.internal.config.ConfigNodeImpl;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

// What the screens call configs: by their own name ("Client", "Server") within a mod's screens, and after their mod's name
// where it matters which mod's they are, never by their full title ("Testingmod Client").
final class ConfigNames {
    private ConfigNames() {}

    /** The config's own name: {@code config.<modid>.<name>.label} when translated, else its name prettified. */
    static Component name(Config config) {
        return Component.translatableWithFallback("config." + config.modId() + "." + config.name() + ".label", ConfigNodeImpl.prettify(config.name()));
    }

    /** The config's name after its mod's: "Testing Mod › Client". */
    static MutableComponent withMod(Config config) {
        return Component.literal(modName(config.modId()) + " › ").append(name(config));
    }

    /** The mod's name as its loader knows it. */
    static String modName(String modId) {
        return Platform.getModName(modId).orElseGet(() -> ConfigNodeImpl.prettify(modId));
    }
}
