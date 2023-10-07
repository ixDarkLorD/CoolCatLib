package net.ixdarklord.coolcat_lib.common.brewing.fabric;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;

public class BrewingRecipe implements IBrewingRecipe
{
    @NotNull private final Ingredient input;
    @NotNull private final Ingredient ingredient;
    @NotNull private final ItemStack output;

    public BrewingRecipe(@NotNull Ingredient input, @NotNull Ingredient ingredient, @NotNull ItemStack output) {
        this.input = input;
        this.ingredient = ingredient;
        this.output = output;
    }

    @Override
    public boolean isInput(@NotNull ItemStack stack) {
        return this.input.test(stack);
    }

    @Override
    public ItemStack getOutput(ItemStack input, ItemStack ingredient) {
        return isInput(input) && isIngredient(ingredient) ? getOutput().copy() : ItemStack.EMPTY;
    }

    public @NotNull Ingredient getInput() {
        return input;
    }

    public @NotNull Ingredient getIngredient() {
        return ingredient;
    }

    public @NotNull ItemStack getOutput() {
        return output;
    }

    @Override
    public boolean isIngredient(ItemStack ingredient) {
        return this.ingredient.test(ingredient);
    }
}
