package net.ixdarklord.coolcatlib.internal.core.neoforge;

import net.ixdarklord.coolcatlib.api.brewing.BrewingRecipe;
import net.ixdarklord.coolcatlib.api.brewing.IBrewingRecipe;
import net.ixdarklord.coolcatlib.api.brewing.neoforge.NeoForgeBrewingRecipe;
import net.ixdarklord.coolcatlib.api.event.v1.server.RegisterBrewingRecipesEvent;
import net.ixdarklord.coolcatlib.api.hooks.ServerLifecycleHooks;
import net.ixdarklord.coolcatlib.internal.core.CoolCatLib;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.NotNull;

@Mod(CoolCatLib.MOD_ID)
public class NeoForgeSetup {
    public NeoForgeSetup(IEventBus modEventBus) {
        IEventBus eventBus = NeoForge.EVENT_BUS;
        eventBus.addListener(NeoForgeSetup::onRegisterBrewingRecipes);
        eventBus.addListener((ServerStartingEvent event) -> ServerLifecycleHooks.updateServerState(event.getServer()));
        eventBus.addListener((ServerStoppedEvent event) -> ServerLifecycleHooks.updateServerState(null));
    }

    private static void onRegisterBrewingRecipes(net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent event) {
        RegisterBrewingRecipesEvent invokeEvent = RegisterBrewingRecipesEvent.invokeEvent(event.getBuilder());
        for (IBrewingRecipe recipe : invokeEvent.getBuilder().getBrewingRecipes()) {
            if (recipe instanceof BrewingRecipe brewingRecipe) {
                event.getBuilder().addRecipe(new NeoForgeBrewingRecipe(brewingRecipe));
                continue;
            }

            event.getBuilder().addRecipe(new net.neoforged.neoforge.common.brewing.IBrewingRecipe() {
                @Override
                public boolean isInput(@NotNull ItemStack arg) {
                    return recipe.isInput(arg);
                }

                @Override
                public boolean isIngredient(@NotNull ItemStack arg) {
                    return recipe.isIngredient(arg);
                }

                @Override
                public @NotNull ItemStack getOutput(@NotNull ItemStack arg, @NotNull ItemStack arg2) {
                    return recipe.getOutput(arg, arg2);
                }
            });
        }
    }
}