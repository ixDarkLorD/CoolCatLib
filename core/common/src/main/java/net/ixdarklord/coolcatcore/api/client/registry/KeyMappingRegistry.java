package net.ixdarklord.coolcatcore.api.client.registry;

import net.ixdarklord.coolcatcore.internal.platform.ClientServices;
import net.minecraft.client.KeyMapping;

/**
 * Key mappings shown in the controls screen. Register while the client initialises.
 */
public final class KeyMappingRegistry {
    private KeyMappingRegistry() {}

    public static KeyMapping register(KeyMapping mapping) {
        ClientServices.get().registerKeyMapping(mapping);
        return mapping;
    }
}
