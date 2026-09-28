package net.ixdarklord.coolcatcore.api.brewing;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;

public record BrewingRecipe(@NotNull Ingredient input, @NotNull Ingredient ingredient, @NotNull ItemStack output) implements IBrewingRecipe {
    @Override
    public boolean isInput(@NotNull ItemStack stack) {
        return this.input.test(stack);
    }

    @Override
    public boolean isIngredient(ItemStack ingredient) {
        return this.ingredient.test(ingredient);
    }
}
