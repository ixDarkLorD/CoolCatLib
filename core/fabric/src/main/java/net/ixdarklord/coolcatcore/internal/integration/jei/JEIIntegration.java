package net.ixdarklord.coolcatcore.internal.integration.jei;

import com.google.common.collect.Lists;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.recipe.vanilla.IJeiBrewingRecipe;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.library.plugins.vanilla.ingredients.subtypes.PotionSubtypeInterpreter;
import mezz.jei.library.util.ResourceLocationUtil;
import net.ixdarklord.coolcatcore.api.brewing.BrewingRecipe;
import net.ixdarklord.coolcatcore.api.brewing.fabric.FabricBrewingRecipes;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;

public class JEIIntegration implements IModPlugin {
    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return CoolCatCore.rl("jei_integration");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<IJeiBrewingRecipe> jeiBrewingRecipes = Lists.newArrayList();
        // Vanilla's mixes come from JEI's own plugin; these are the custom recipes.
        List<BrewingRecipe> brewingRecipes = FabricBrewingRecipes.getRecipes().stream()
                .peek(recipe -> {
                    if (!(recipe instanceof BrewingRecipe)) {
                        CoolCatCore.LOGGER.warn("Skipping {} in JEI: Not a valid BrewingRecipe subclass.",
                                recipe.getClass().getSimpleName());
                    }
                })
                .filter(recipe -> recipe instanceof BrewingRecipe)
                .map(recipe -> ((BrewingRecipe) recipe))
                .toList();

        for (BrewingRecipe recipe : brewingRecipes) {
            List<ItemStack> ingredients = List.of(recipe.ingredient().getItems());
            List<ItemStack> inputs = List.of(recipe.input().getItems());
            if (ingredients.isEmpty() || inputs.isEmpty()) continue;

            IIngredientHelper<ItemStack> itemStackHelper = registration.getIngredientManager().getIngredientHelper(VanillaTypes.ITEM_STACK);
            String inputPathId = pathId(itemStackHelper, inputs.get(0));
            ResourceLocation outputResourceLocation = itemStackHelper.getResourceLocation(recipe.output());
            String outputPathId = pathId(itemStackHelper, recipe.output());
            String outputModId = outputResourceLocation.getNamespace();
            ResourceLocation uidPath = new ResourceLocation(outputModId, ResourceLocationUtil.sanitizePath(inputPathId + ".to." + outputPathId));
            ResourceLocation potionUid = uidPath;

            long dupesCount = jeiBrewingRecipes.stream()
                    .filter(iJeiBrewingRecipe -> iJeiBrewingRecipe.getUid() != null)
                    .filter(iJeiBrewingRecipe -> uidPath.toString().contains(iJeiBrewingRecipe.getUid().toString()))
                    .count();

            if (dupesCount > 0L) {
                potionUid = uidPath.withSuffix("_" + (dupesCount+1));
            }

            jeiBrewingRecipes.add(registration.getVanillaRecipeFactory().createBrewingRecipe(ingredients, inputs, recipe.output(), potionUid));
        }
        jeiBrewingRecipes.sort(Comparator.comparingInt(IJeiBrewingRecipe::getBrewingSteps));
        registration.addRecipes(RecipeTypes.BREWING, jeiBrewingRecipes);
    }

    // The potion's name for potions (as JEI names its own brewing recipes), the item's id otherwise.
    private static String pathId(IIngredientHelper<ItemStack> helper, ItemStack stack) {
        String potion = PotionSubtypeInterpreter.INSTANCE.apply(stack, UidContext.Recipe);
        return potion.isEmpty() ? helper.getResourceLocation(stack).getPath() : potion;
    }
}
