package net.ixdarklord.coolcatcore.api.brewing.fabric;

import net.ixdarklord.coolcatcore.api.brewing.BrewingRecipeRegistry;
import net.ixdarklord.coolcatcore.api.brewing.IBrewingRecipe;
import net.ixdarklord.coolcatcore.api.event.v1.server.RegisterBrewingRecipesEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/**
 * The custom brewing recipes on Fabric, next to vanilla's (static) {@link net.minecraft.world.item.alchemy.PotionBrewing}
 * mixes, which the brewing stand checks first. They're registered through {@link RegisterBrewingRecipesEvent}, fired
 * once the game has started (see {@link #registerRecipes()}); until then there are none.
 */
public final class FabricBrewingRecipes {
    private static volatile BrewingRecipeRegistry registry = new BrewingRecipeRegistry(List.of());
    private static boolean registered;

    private FabricBrewingRecipes() {}

    /**
     * Retrieves recipes that use the more general interface.
     * This does NOT include the container and potion mixes.
     */
    public static List<IBrewingRecipe> getRecipes() {
        return registry.recipes();
    }

    /**
     * Checks if an item stack is a valid input for brewing,
     * for use in the lower 3 slots where water bottles would normally go.
     */
    public static boolean isInput(ItemStack stack) {
        return registry.isValidInput(stack) || stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION);
    }

    @ApiStatus.Internal
    public static BrewingRecipeRegistry registry() {
        return registry;
    }

    /**
     * Fires {@link RegisterBrewingRecipesEvent} and keeps its recipes; only the first call does anything. Called once the
     * client has started, or as a dedicated server starts, so every mod has registered its listeners.
     */
    @ApiStatus.Internal
    public static synchronized void registerRecipes() {
        if (registered) return;
        registered = true;
        RegisterBrewingRecipesEvent event = RegisterBrewingRecipesEvent.invokeEvent();
        registry = new BrewingRecipeRegistry(List.copyOf(event.getBuilder().getBrewingRecipes()));
    }
}
