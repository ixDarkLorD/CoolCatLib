package net.ixdarklord.coolcatlib.internal.integration.jei;

import com.google.common.collect.Lists;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.recipe.vanilla.IJeiBrewingRecipe;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.common.util.ErrorUtil;
import mezz.jei.library.plugins.vanilla.ingredients.subtypes.PotionSubtypeInterpreter;
import mezz.jei.library.util.ResourceLocationUtil;
import net.ixdarklord.coolcatlib.api.brewing.BrewingRecipe;
import net.ixdarklord.coolcatlib.api.brewing.fabric.ext.PotionBrewingExt;
import net.ixdarklord.coolcatlib.internal.core.CoolCatLib;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class JEIIntegration implements IModPlugin {
    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return CoolCatLib.rl("jei_integration");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        Level level = Minecraft.getInstance().level;
        ErrorUtil.checkNotNull(level, "minecraft.level");

        List<IJeiBrewingRecipe> jeiBrewingRecipes = Lists.newArrayList();
        PotionBrewing potionBrewing = level.potionBrewing();
        List<BrewingRecipe> brewingRecipes = ((PotionBrewingExt) potionBrewing).getRecipes().stream()
                .peek(recipe -> {
                    if (!(recipe instanceof BrewingRecipe)) {
                        CoolCatLib.LOGGER.warn("Skipping {} in JEI: Not a valid BrewingRecipe subclass.",
                                recipe.getClass().getSimpleName());
                    }
                })
                .filter(recipe -> recipe instanceof BrewingRecipe)
                .map(recipe -> ((BrewingRecipe) recipe))
                .toList();

        for (BrewingRecipe recipe : brewingRecipes) {
            List<ItemStack> ingredients = Arrays.asList(recipe.getIngredient().getItems());
            List<ItemStack> inputs = Arrays.asList(recipe.getInput().getItems());

            IIngredientHelper<ItemStack> itemStackHelper = registration.getIngredientManager().getIngredientHelper(VanillaTypes.ITEM_STACK);
            String inputPathId = PotionSubtypeInterpreter.INSTANCE.getStringName(recipe.getInput().getItems()[0]);
            ResourceLocation outputResourceLocation = itemStackHelper.getResourceLocation(recipe.getOutput());
            String outputPathId = PotionSubtypeInterpreter.INSTANCE.getStringName(recipe.getOutput());
            String outputModId = outputResourceLocation.getNamespace();
            ResourceLocation uidPath = ResourceLocation.fromNamespaceAndPath(outputModId, ResourceLocationUtil.sanitizePath(inputPathId + ".to." + outputPathId));
            ResourceLocation potionUid = uidPath;

            long dupesCount = jeiBrewingRecipes.stream()
                    .filter(iJeiBrewingRecipe -> iJeiBrewingRecipe.getUid() != null)
                    .filter(iJeiBrewingRecipe -> uidPath.toString().contains(iJeiBrewingRecipe.getUid().toString()))
                    .count();

            if (dupesCount > 0L) {
                potionUid = uidPath.withSuffix("_" + (dupesCount+1));
            }

            jeiBrewingRecipes.add(registration.getVanillaRecipeFactory().createBrewingRecipe(ingredients, inputs, recipe.getOutput(), potionUid));
        }
        jeiBrewingRecipes.sort(Comparator.comparingInt(IJeiBrewingRecipe::getBrewingSteps));
        registration.addRecipes(RecipeTypes.BREWING, jeiBrewingRecipes);
    }
}