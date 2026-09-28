package net.ixdarklord.coolcatcore.internal.core.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.ixdarklord.coolcatcore.api.brewing.fabric.FabricBrewingRecipes;
import net.ixdarklord.coolcatcore.api.core.DataGenerationConstructor;
import net.ixdarklord.coolcatcore.api.core.ModConstructor;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCoreConstructor;
import net.ixdarklord.coolcatcore.internal.event.fabric.FabricEventHooks;
import net.ixdarklord.coolcatcore.internal.platform.fabric.FabricTransferCompat;

public class FabricSetup implements ModInitializer {
    @Override
    public void onInitialize() {
        FabricEventHooks.register();
        FabricTransferCompat.register();
        // Brewing recipes are global on 1.20.1: registered once every mod is initialized (a client does it as it starts).
        ServerLifecycleEvents.SERVER_STARTING.register(server -> FabricBrewingRecipes.registerRecipes());
        ModConstructor.construct(CoolCatCore.MOD_ID, CoolCatCoreConstructor::new);
        FabricEntrypoints.construct(FabricEntrypoints.COMMON, ModConstructor.class, ModConstructor::construct);
        FabricEntrypoints.construct(FabricEntrypoints.DATA_GENERATION, DataGenerationConstructor.class, DataGenerationConstructor::construct);
    }
}
