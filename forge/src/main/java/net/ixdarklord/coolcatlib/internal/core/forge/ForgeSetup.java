package net.ixdarklord.coolcatlib.internal.core.forge;

import net.ixdarklord.coolcatlib.api.brewing.IBrewingRecipe;
import net.ixdarklord.coolcatlib.api.brewing.forge.ForgeBrewingRecipe;
import net.ixdarklord.coolcatlib.api.event.v1.server.RegisterBrewingRecipesEvent;
import net.ixdarklord.coolcatlib.api.hooks.ServerLifecycleHooks;
import net.ixdarklord.coolcatlib.internal.core.CoolCatLib;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(CoolCatLib.MOD_ID)
public class ForgeSetup {
    public ForgeSetup() {
        IEventBus eventBus = MinecraftForge.EVENT_BUS;
        eventBus.addListener(ForgeSetup::onCommonSetup);
        eventBus.addListener((ServerStartingEvent event) -> ServerLifecycleHooks.updateServerState(event.getServer()));
        eventBus.addListener((ServerStoppedEvent event) -> ServerLifecycleHooks.updateServerState(null));
    }

    private static void onCommonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            RegisterBrewingRecipesEvent invokeEvent = RegisterBrewingRecipesEvent.invokeEvent();
            net.ixdarklord.coolcatlib.api.brewing.BrewingRecipeRegistry registry = invokeEvent.getBuilder().build();
            for (IBrewingRecipe recipe : registry.recipes()) {
                BrewingRecipeRegistry.addRecipe(new ForgeBrewingRecipe(recipe));
            }
        });
    }
}