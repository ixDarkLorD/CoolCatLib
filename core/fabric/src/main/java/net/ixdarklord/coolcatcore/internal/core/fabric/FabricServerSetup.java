package net.ixdarklord.coolcatcore.internal.core.fabric;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.ixdarklord.coolcatcore.api.core.ServerModConstructor;

public class FabricServerSetup implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        FabricEntrypoints.construct(FabricEntrypoints.SERVER, ServerModConstructor.class, ServerModConstructor::construct);
    }
}
