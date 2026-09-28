package net.ixdarklord.coolcatcore.internal.core.forge;

import net.ixdarklord.coolcatcore.api.brewing.IBrewingRecipe;
import net.ixdarklord.coolcatcore.api.brewing.forge.ForgeBrewingRecipe;
import net.ixdarklord.coolcatcore.api.core.forge.ForgeModEntrypoint;
import net.ixdarklord.coolcatcore.api.event.v1.server.RegisterBrewingRecipesEvent;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCoreConstructor;
import net.ixdarklord.coolcatcore.internal.event.forge.ForgeEventHooks;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(CoolCatCore.MOD_ID)
public class ForgeSetup extends ForgeModEntrypoint {
    public ForgeSetup(FMLJavaModLoadingContext context) {
        super(context);
        // FMLCommonSetupEvent is a mod bus event: listening on Forge's bus never fired it.
        this.modEventBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(ForgeSetup::registerBrewingRecipes));
        ForgeEventHooks.register(this.modEventBus);
        this.common(CoolCatCoreConstructor::new);
        // Forge 47 has no client-only @Mod classes; the client half is only loaded on a client.
        if (FMLEnvironment.dist.isClient()) ForgeClientSetup.init(this.modEventBus);
    }

    // Brewing recipes are global on 1.20.1 (Forge's BrewingRecipeRegistry): registered once, on the main thread.
    private static void registerBrewingRecipes() {
        RegisterBrewingRecipesEvent event = RegisterBrewingRecipesEvent.invokeEvent();
        for (IBrewingRecipe recipe : event.getBuilder().getBrewingRecipes()) {
            BrewingRecipeRegistry.addRecipe(new ForgeBrewingRecipe(recipe));
        }
    }
}
