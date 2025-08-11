package net.ixdarklord.coolcatlib.internal.core.forge;

import net.ixdarklord.coolcatlib.api.brewing.IBrewingRecipe;
import net.ixdarklord.coolcatlib.api.brewing.forge.ForgeBrewingRecipe;
import net.ixdarklord.coolcatlib.api.event.v1.server.RegisterBrewingRecipesEvent;
import net.ixdarklord.coolcatlib.api.hooks.ServerLifecycleHooks;
import net.ixdarklord.coolcatlib.internal.core.CoolCatLib;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.brewing.BrewingRecipeRegisterEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;

@Mod(CoolCatLib.MOD_ID)
public class ForgeSetup {
    public ForgeSetup() {
        IEventBus eventBus = MinecraftForge.EVENT_BUS;
        eventBus.addListener(ForgeSetup::onRegisterBrewingRecipes);
        eventBus.addListener((ServerStartingEvent event) -> ServerLifecycleHooks.updateServerState(event.getServer()));
        eventBus.addListener((ServerStoppedEvent event) -> ServerLifecycleHooks.updateServerState(null));
    }

    private static void onRegisterBrewingRecipes(BrewingRecipeRegisterEvent event) {
        RegisterBrewingRecipesEvent invokeEvent = RegisterBrewingRecipesEvent.invokeEvent(event.getBuilder());
        for (IBrewingRecipe recipe : invokeEvent.getBuilder().getBrewingRecipes()) {
            event.getBuilder().add(new ForgeBrewingRecipe(recipe));
        }
    }
}