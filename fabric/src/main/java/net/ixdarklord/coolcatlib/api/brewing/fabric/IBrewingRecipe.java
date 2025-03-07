package net.ixdarklord.coolcatlib.api.brewing.fabric;

import net.ixdarklord.coolcatlib.api.brewing.fabric.ext.PotionBrewingBuilderExt;
import net.minecraft.world.item.ItemStack;

/**
 * Interface for more flexible brewing recipes.
 *
 * <p>Register using {@link RegisterBrewingRecipesEvent} and {@link PotionBrewingBuilderExt#addRecipe(IBrewingRecipe)}.
 */
public interface IBrewingRecipe {
    /**
     * Returns true is the passed ItemStack is an input for this recipe. "Input"
     * being the item that goes in one of the three bottom slots of the brewing
     * stand (e.g: water bottle)
     */
    boolean isInput(ItemStack input);

    /**
     * Returns true if the passed ItemStack is an ingredient for this recipe.
     * "Ingredient" being the item that goes in the top slot of the brewing
     * stand (e.g: nether wart)
     */
    boolean isIngredient(ItemStack ingredient);

    /**
     * Returns the output when the passed input is brewed with the passed
     * ingredient. Empty if invalid input or ingredient.
     */
    ItemStack getOutput(ItemStack input, ItemStack ingredient);
}