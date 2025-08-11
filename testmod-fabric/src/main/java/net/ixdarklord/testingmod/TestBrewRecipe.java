package net.ixdarklord.testingmod;

import net.ixdarklord.coolcatlib.api.brewing.BrewingRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public class TestBrewRecipe extends BrewingRecipe {
    public TestBrewRecipe(Ingredient input, Ingredient ingredient, ItemStack output) {
        super(input, ingredient, output);
    }
}
