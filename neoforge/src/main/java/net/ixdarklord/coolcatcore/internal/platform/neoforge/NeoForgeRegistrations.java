package net.ixdarklord.coolcatcore.internal.platform.neoforge;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

// What NeoForge only accepts through its registration events, collected until those events fire
// (see NeoForgeEventHooks / NeoForgeClientEventHooks). Mods add to these while they're constructed.
public final class NeoForgeRegistrations {
    public static final List<Consumer<PayloadRegistrar>> PAYLOADS = new ArrayList<>();
    public static boolean payloadsRegistered;
    // Kept for every reload, not drained.
    public static final Map<ResourceLocation, PreparableReloadListener> SERVER_RELOAD_LISTENERS = new LinkedHashMap<>();
    // Sent to each client as its configuration starts, by one configuration task.
    public static final List<Supplier<List<CustomPacketPayload>>> CONFIGURATION_PAYLOADS = new CopyOnWriteArrayList<>();

    private NeoForgeRegistrations() {}

    public static void addPayload(Consumer<PayloadRegistrar> registration) {
        if (payloadsRegistered) {
            throw new IllegalStateException("Payloads must be registered while mods are constructed; NeoForge has already collected them");
        }
        PAYLOADS.add(registration);
    }
}
