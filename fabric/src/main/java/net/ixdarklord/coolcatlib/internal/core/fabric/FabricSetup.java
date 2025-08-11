package net.ixdarklord.coolcatlib.internal.core.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.ixdarklord.coolcatlib.api.event.v1.server.RegisterBrewingRecipesEvent;
import net.ixdarklord.coolcatlib.api.hooks.ServerLifecycleHooks;

public class FabricSetup implements ModInitializer {
    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            ServerLifecycleHooks.updateServerState(server);
            RegisterBrewingRecipesEvent.invokeEvent().getBuilder().build();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(minecraftServer ->
                ServerLifecycleHooks.updateServerState(null));
    }
}
