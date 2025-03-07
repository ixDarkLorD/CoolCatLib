package net.ixdarklord.coolcatlib.internal.integration.jei;

import com.google.common.collect.Lists;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.recipe.vanilla.IJeiBrewingRecipe;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.common.util.ErrorUtil;
import mezz.jei.library.util.ResourceLocationUtil;
import net.ixdarklord.coolcatlib.api.brewing.fabric.BrewingRecipe;
import net.ixdarklord.coolcatlib.api.brewing.fabric.ext.PotionBrewingExt;
import net.ixdarklord.coolcatlib.internal.CoolCatLib;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class JeiFabricIntegration implements IModPlugin {

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
                .filter(iBrewingRecipe -> iBrewingRecipe instanceof BrewingRecipe)
                .map(iBrewingRecipe -> ((BrewingRecipe) iBrewingRecipe))
                .toList();

        for (BrewingRecipe recipe : brewingRecipes) {
            List<ItemStack> ingredients = Arrays.asList(recipe.getIngredient().getItems());
            List<ItemStack> inputs = Arrays.asList(recipe.getInput().getItems());

            IIngredientHelper<ItemStack> ingredientHelper = registration.getIngredientManager().getIngredientHelper(VanillaTypes.ITEM_STACK);
            String modId = ingredientHelper.getResourceLocation(recipe.getOutput()).getNamespace();
            String inputPotionId = Arrays.stream(recipe.getInput().getItems())
                    .map(itemStack -> {
                        PotionContents contents = itemStack.get(DataComponents.POTION_CONTENTS);
                        if (contents != null) {
                            String potionEffectId = contents.potion()
                                    .map(Holder::getRegisteredName)
                                    .map(s -> s.replaceAll("minecraft:", "").replaceAll(":", "."))
                                    .orElse("none");
                            return ResourceLocation.fromNamespaceAndPath(modId, "potion." + potionEffectId);
                        }
                        return ingredientHelper.getResourceLocation(itemStack);
                    })
                    .map(ResourceLocation::getPath)
                    .collect(Collectors.joining("-"));

            String outputPotionId = ingredientHelper.getResourceLocation(recipe.getOutput()).getPath();
            String convertUid = ResourceLocationUtil.sanitizePath(inputPotionId + ".to." + outputPotionId);
            final ResourceLocation uid = ResourceLocation.fromNamespaceAndPath(modId, convertUid);
            long i = jeiBrewingRecipes.stream().filter(iJeiBrewingRecipe -> uid.equals(iJeiBrewingRecipe.getUid())).count();
            ResourceLocation potionUid = i > 0
                    ? uid.withSuffix("_" + i)
                    : uid;
            System.out.println(potionUid);
            jeiBrewingRecipes.add(registration.getVanillaRecipeFactory().createBrewingRecipe(ingredients, inputs, recipe.getOutput(), potionUid));
        }
        jeiBrewingRecipes.sort(Comparator.comparingInt(IJeiBrewingRecipe::getBrewingSteps));
        registration.addRecipes(RecipeTypes.BREWING, jeiBrewingRecipes);
    }
}