package net.ixdarklord.coolcatlib.api.brewing;

import java.util.List;

import net.ixdarklord.coolcatlib.api.event.v1.server.RegisterBrewingRecipesEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import org.jetbrains.annotations.ApiStatus;

/**
 * Internal registry for custom brewing recipes.
 * <p>
 * This registry stores all {@link IBrewingRecipe} instances and provides
 * utility methods for querying brewing outputs, inputs, and ingredients.
 * <p>
 * <b>Usage:</b>
 * <ul>
 *   <li>For <b>queries</b>, use the vanilla {@link PotionBrewing} when possible.</li>
 *   <li>For <b>registration</b>, use {@link RegisterBrewingRecipesEvent}.</li>
 * </ul>
 */
@ApiStatus.Internal
public record BrewingRecipeRegistry(List<IBrewingRecipe> recipes) {

    /**
     * Gets the output of brewing the given input with the given ingredient.
     * <p>
     * If the input or ingredient is empty, or if the input stack count is not exactly 1,
     * returns {@link ItemStack#EMPTY}.
     *
     * @param input      the brewing stand's bottom-slot item
     * @param ingredient the brewing stand's top-slot item
     * @return the resulting {@link ItemStack}, or {@link ItemStack#EMPTY} if no matching recipe exists
     */
    public ItemStack getOutput(ItemStack input, ItemStack ingredient) {
        if (input.isEmpty() || input.getCount() != 1) return ItemStack.EMPTY;
        if (ingredient.isEmpty()) return ItemStack.EMPTY;

        for (IBrewingRecipe recipe : recipes) {
            ItemStack output = recipe.getOutput(input, ingredient);
            if (!output.isEmpty()) {
                return output;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Checks whether there is any recipe that produces an output for the given input and ingredient.
     *
     * @param input      the brewing stand's bottom-slot item
     * @param ingredient the brewing stand's top-slot item
     * @return {@code true} if there is a matching recipe, otherwise {@code false}
     */
    public boolean hasOutput(ItemStack input, ItemStack ingredient) {
        return !getOutput(input, ingredient).isEmpty();
    }

    /**
     * Checks whether the given {@link ItemStack} is a valid ingredient for any recipe in this registry.
     *
     * @param stack the item to test
     * @return {@code true} if it is a valid ingredient, otherwise {@code false}
     */
    public boolean isValidIngredient(ItemStack stack) {
        if (stack.isEmpty()) return false;

        for (IBrewingRecipe recipe : recipes) {
            if (recipe.isIngredient(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether the given {@link ItemStack} is a valid input for any recipe in this registry.
     *
     * @param stack the item to test
     * @return {@code true} if it is a valid input, otherwise {@code false}
     */
    public boolean isValidInput(ItemStack stack) {
        for (IBrewingRecipe recipe : recipes) {
            if (recipe.isInput(stack)) {
                return true;
            }
        }
        return false;
    }
}
