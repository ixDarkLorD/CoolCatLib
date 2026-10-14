package net.ixdarklord.coolcatcore.internal.platform.forge;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

// What Forge only accepts through its events, collected until those events fire (see ForgeEventHooks). Mods add to
// these while they're constructed, which Forge does in parallel.
public final class ForgeRegistrations {
    // Kept for every reload, not drained.
    public static final Map<ResourceLocation, PreparableReloadListener> SERVER_RELOAD_LISTENERS = Collections.synchronizedMap(new LinkedHashMap<>());
    // Sent to each client as its configuration starts, by one configuration task.
    public static final List<Supplier<List<CustomPacketPayload>>> CONFIGURATION_PAYLOADS = new CopyOnWriteArrayList<>();

    private ForgeRegistrations() {}
}
