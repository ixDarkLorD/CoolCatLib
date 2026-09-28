package net.ixdarklord.coolcatcore.internal.platform.forge;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// What Forge only accepts through its events, collected until those events fire. Mods add to these while they're
// constructed, which Forge does in parallel. CoolCatLib: Core's Forge entrypoint calls register once.
public final class ForgeRegistrations {
    // Kept for every reload, not drained.
    public static final Map<ResourceLocation, PreparableReloadListener> SERVER_RELOAD_LISTENERS = Collections.synchronizedMap(new LinkedHashMap<>());

    private ForgeRegistrations() {}

    /**
     * Hooks the common registrations into Forge and creates CoolCatLib: Core's network channel. Called once while
     * CoolCatLib: Core is constructed; {@code modBus} is its mod bus (nothing needs it yet).
     */
    public static void register(IEventBus modBus) {
        ForgeNetworking.init();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, AddReloadListenerEvent.class, ForgeRegistrations::onAddReloadListeners);
    }

    // Forge orders reload listeners by when they're added; ids only matter on Fabric.
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        List<PreparableReloadListener> listeners;
        synchronized (SERVER_RELOAD_LISTENERS) {
            listeners = new ArrayList<>(SERVER_RELOAD_LISTENERS.values());
        }
        listeners.forEach(event::addListener);
    }
}
