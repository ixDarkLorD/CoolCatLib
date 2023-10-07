package net.ixdarklord.coolcat_lib.integration.jei;

import com.google.common.collect.Lists;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.vanilla.IJeiBrewingRecipe;
import mezz.jei.api.registration.IRecipeRegistration;
import net.ixdarklord.coolcat_lib.common.brewing.fabric.BrewingRecipeRegistry;
import net.ixdarklord.coolcat_lib.core.Constants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class JeiFabricIntegration implements IModPlugin {
    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return Constants.getLocation("jei_fabric_integration");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<IJeiBrewingRecipe> brewingRecipes = Lists.newArrayList();
        BrewingRecipeRegistry.getBrewingRecipes().forEach(recipe -> {
            List<ItemStack> ingredients = Arrays.asList(recipe.getIngredient().getItems());
            List<ItemStack> inputs = Arrays.asList(recipe.getInput().getItems());
            brewingRecipes.add(registration.getVanillaRecipeFactory().createBrewingRecipe(ingredients, inputs, recipe.getOutput()));
        });
        brewingRecipes.sort(Comparator.comparingInt(IJeiBrewingRecipe::getBrewingSteps));
        registration.addRecipes(RecipeTypes.BREWING, brewingRecipes);
    }
}
