package net.ixdarklord.coolcatlib.api.brewing.neoforge;

import net.ixdarklord.coolcatlib.api.brewing.IBrewingRecipe;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.brewing.BrewingRecipe;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class NeoForgeBrewingRecipe extends BrewingRecipe {
    private final IBrewingRecipe recipe;

    public NeoForgeBrewingRecipe(IBrewingRecipe recipe) {
        super(recipe.input(), recipe.ingredient(), recipe.output());
        this.recipe = recipe;
    }

    @Override
    public boolean isInput(@NotNull ItemStack input) {
        return recipe.isInput(input);
    }

    @Override
    public boolean isIngredient(@NotNull ItemStack ingredient) {
        return recipe.isIngredient(ingredient);
    }

    @Override
    public @NotNull ItemStack getOutput(@NotNull ItemStack input, @NotNull ItemStack ingredient) {
        return recipe.getOutput(input, ingredient);
    }
}
