package net.ixdarklord.coolcatlib.api.brewing;

import net.ixdarklord.coolcatlib.api.event.v1.server.RegisterBrewingRecipesEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a flexible brewing recipe for the Minecraft brewing stand.
 * <p>
 * Implementations of this interface define custom brewing logic
 * beyond vanilla recipes. Recipes should be registered via
 * {@link RegisterBrewingRecipesEvent}.
 */
public interface IBrewingRecipe {

    /**
     * Checks whether the given {@link ItemStack} is a valid input for this recipe.
     * <p>
     * "Input" refers to the item placed in one of the three bottom slots of the brewing stand
     * (e.g., a water bottle).
     *
     * @param input the {@link ItemStack} to test
     * @return {@code true} if the item is a valid input, otherwise {@code false}
     */
    boolean isInput(ItemStack input);

    /**
     * Checks whether the given {@link ItemStack} is a valid ingredient for this recipe.
     * <p>
     * "Ingredient" refers to the item placed in the top slot of the brewing stand
     * (e.g., nether wart).
     *
     * @param ingredient the {@link ItemStack} to test
     * @return {@code true} if the item is a valid ingredient, otherwise {@code false}
     */
    boolean isIngredient(ItemStack ingredient);

    /**
     * Gets the input requirement for this recipe.
     *
     * @return the required input as an {@link Ingredient}
     */
    @NotNull
    Ingredient getInput();

    /**
     * Gets the ingredient requirement for this recipe.
     *
     * @return the required ingredient as an {@link Ingredient}
     */
    @NotNull
    Ingredient getIngredient();

    /**
     * Gets the result of brewing the valid input with the valid ingredient.
     *
     * @return the resulting {@link ItemStack}
     */
    @NotNull
    ItemStack getOutput();

    /**
     * Computes the resulting output when brewing the given input with the given ingredient.
     * <p>
     * If either the input or ingredient is invalid, returns {@link ItemStack#EMPTY}.
     *
     * @param input      the brewing stand's bottom slot item
     * @param ingredient the brewing stand's top slot item
     * @return the resulting {@link ItemStack} if valid, otherwise {@link ItemStack#EMPTY}
     */
    @NotNull
    default ItemStack getOutput(ItemStack input, ItemStack ingredient) {
        return isInput(input) && isIngredient(ingredient) ? getOutput().copy() : ItemStack.EMPTY;
    }
}