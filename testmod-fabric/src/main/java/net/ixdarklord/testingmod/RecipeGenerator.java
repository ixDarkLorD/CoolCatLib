package net.ixdarklord.testingmod;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.ixdarklord.coolcatlib.internal.CoolCatLib;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Items;

import java.util.concurrent.CompletableFuture;

public class RecipeGenerator extends FabricRecipeProvider {
    public RecipeGenerator(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    public void buildRecipes(RecipeOutput exporter) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Items.NETHERITE_BLOCK)
                .define('P', Items.PAPER)
                .define('1', Items.DIAMOND_PICKAXE).define('2', Items.IRON_AXE)
                .define('3', Items.GOLDEN_HOE).define('4', Items.STONE_SHOVEL)
                .pattern(" 1 ")
                .pattern("2P3")
                .pattern(" 4 ")
                .unlockedBy("has_paper", inventoryTrigger(ItemPredicate.Builder.item().of(Items.PAPER)))
                .save(withConditions(exporter, ResourceConditions.alwaysTrue()), CoolCatLib.rl("testing_recipe"));
    }
}
