package net.ixdarklord.coolcatcore.internal.core.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.ixdarklord.coolcatcore.api.brewing.fabric.FabricBrewingRecipes;
import net.ixdarklord.coolcatcore.api.core.ClientModConstructor;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.ixdarklord.coolcatcore.internal.core.client.CoolCatCoreClientConstructor;
import net.ixdarklord.coolcatcore.internal.event.fabric.FabricClientEventHooks;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.ixdarklord.coolcatcore.api.item.ComponentItem;

public class FabricClientSetup implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ItemTooltipCallback.EVENT.register((stack, flag, lines) -> ComponentItem.onTooltip(stack, lines));
        FabricClientEventHooks.register();
        ClientLifecycleEvents.CLIENT_STARTED.register(minecraft -> FabricBrewingRecipes.registerRecipes());
        ClientModConstructor.construct(CoolCatCore.MOD_ID, CoolCatCoreClientConstructor::new);
        FabricEntrypoints.construct(FabricEntrypoints.CLIENT, ClientModConstructor.class, ClientModConstructor::construct);
    }
}
