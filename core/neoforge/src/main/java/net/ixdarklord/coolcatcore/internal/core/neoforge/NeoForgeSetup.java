package net.ixdarklord.coolcatcore.internal.core.neoforge;

import net.ixdarklord.coolcatcore.api.core.neoforge.NeoForgeModEntrypoint;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCoreConstructor;
import net.ixdarklord.coolcatcore.internal.event.neoforge.NeoForgeEventHooks;
import net.ixdarklord.coolcatcore.api.brewing.IBrewingRecipe;
import net.ixdarklord.coolcatcore.api.brewing.neoforge.NeoForgeBrewingRecipe;
import net.ixdarklord.coolcatcore.api.event.v1.server.RegisterBrewingRecipesEvent;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(CoolCatCore.MOD_ID)
public class NeoForgeSetup extends NeoForgeModEntrypoint {
    public NeoForgeSetup(ModContainer container) {
        super(container);
        NeoForge.EVENT_BUS.addListener(NeoForgeSetup::onRegisterBrewingRecipes);
        NeoForgeEventHooks.register(this.modEventBus);
        this.common(CoolCatCoreConstructor::new);
    }

    private static void onRegisterBrewingRecipes(net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent event) {
        RegisterBrewingRecipesEvent invokeEvent = RegisterBrewingRecipesEvent.invokeEvent(event.getBuilder());
        for (IBrewingRecipe recipe : invokeEvent.getBuilder().getBrewingRecipes()) {
            event.getBuilder().addRecipe(new NeoForgeBrewingRecipe(recipe));
        }
    }
}
