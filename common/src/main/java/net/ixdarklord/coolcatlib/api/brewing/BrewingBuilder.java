package net.ixdarklord.coolcatlib.api.brewing;

import com.google.common.collect.Lists;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class BrewingBuilder {
    private final List<IBrewingRecipe> brewingRecipes = Lists.newArrayList();

    public BrewingBuilder() {
        // NO-OP
    }

    public void addRecipe(@NotNull Ingredient input, @NotNull Ingredient ingredient, @NotNull ItemStack output) {
        this.addRecipe(new BrewingRecipe(input, ingredient, output));
    }

    public void addRecipe(@NotNull IBrewingRecipe recipe) {
        this.brewingRecipes.add(recipe);
    }

    public @NotNull BrewingRecipeRegistry build() {
        return new BrewingRecipeRegistry(brewingRecipes);
    }
}
