package net.ixdarklord.coolcatlib.api.brewing.fabric.ext;

import net.ixdarklord.coolcatlib.api.brewing.fabric.BrewingRecipeRegistry;
import net.ixdarklord.coolcatlib.api.brewing.fabric.IBrewingRecipe;
import net.ixdarklord.coolcatlib.internal.CoolCatLib;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;

public interface PotionBrewingExt {
    /**
     * Checks if an item stack is a valid input for brewing,
     * for use in the lower 3 slots where water bottles would normally go.
     */
    default boolean isInput(ItemStack stack) {
        throw CoolCatLib.createMixinException(this.getClass().getSimpleName() + " does not support isInput(ItemStack)");
    }

    /**
     * Retrieves recipes that use the more general interface.
     * This does NOT include the container and potion mixes.
     */
    default List<IBrewingRecipe> getRecipes() {
        throw CoolCatLib.createMixinException(this.getClass().getSimpleName() + " does not support getRecipes()");
    }

    @ApiStatus.Internal
    default void setBrewingRegistry(BrewingRecipeRegistry registry) {
        throw CoolCatLib.createMixinException(this.getClass().getSimpleName() + " does not support setBrewingRegistry(BrewingRecipeRegistry)");
    }
}