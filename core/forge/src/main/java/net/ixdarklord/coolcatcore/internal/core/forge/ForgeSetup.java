package net.ixdarklord.coolcatcore.internal.core.forge;

import net.ixdarklord.coolcatcore.api.brewing.IBrewingRecipe;
import net.ixdarklord.coolcatcore.api.brewing.forge.ForgeBrewingRecipe;
import net.ixdarklord.coolcatcore.api.core.forge.ForgeModEntrypoint;
import net.ixdarklord.coolcatcore.api.event.v1.server.RegisterBrewingRecipesEvent;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCoreConstructor;
import net.ixdarklord.coolcatcore.internal.core.client.CoolCatCoreClientConstructor;
import net.ixdarklord.coolcatcore.internal.event.forge.ForgeEventHooks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.brewing.BrewingRecipeRegisterEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

// Forge allows one @Mod class per mod, so the client half (NeoForge's separate client @Mod) runs from here on a client.
@Mod(CoolCatCore.MOD_ID)
public class ForgeSetup extends ForgeModEntrypoint {
    public ForgeSetup(FMLJavaModLoadingContext context) {
        super(context);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, BrewingRecipeRegisterEvent.class, ForgeSetup::onRegisterBrewingRecipes);
        ForgeEventHooks.register();
        this.common(CoolCatCoreConstructor::new);
        if (FMLEnvironment.dist == Dist.CLIENT) ForgeClientSetup.init(this.modEventBus);
        this.client(() -> CoolCatCoreClientConstructor::new);
    }

    private static void onRegisterBrewingRecipes(BrewingRecipeRegisterEvent event) {
        RegisterBrewingRecipesEvent invokeEvent = RegisterBrewingRecipesEvent.invokeEvent(event.getBuilder());
        for (IBrewingRecipe recipe : invokeEvent.getBuilder().getBrewingRecipes()) {
            event.addRecipe(new ForgeBrewingRecipe(recipe));
        }
    }
}
