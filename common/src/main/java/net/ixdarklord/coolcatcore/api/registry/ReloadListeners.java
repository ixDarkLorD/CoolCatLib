package net.ixdarklord.coolcatcore.api.registry;

import net.ixdarklord.coolcatcore.internal.platform.CommonServices;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;

/**
 * Resource reload listeners.
 */
public final class ReloadListeners {
    private ReloadListeners() {}

    /**
     * Adds a listener to the server's data reloads (world load and {@code /reload}). Call while the mod initialises.
     */
    public static void registerServer(Identifier id, PreparableReloadListener listener) {
        CommonServices.get().registerServerReloadListener(id, listener);
    }
}
