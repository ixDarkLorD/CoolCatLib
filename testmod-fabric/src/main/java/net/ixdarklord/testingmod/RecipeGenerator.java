package net.ixdarklord.testingmod;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.ixdarklord.coolcatlib.internal.core.CoolCatLib;
import net.minecraft.advancements.criterion.ItemPredicate;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.CompletableFuture;

public class RecipeGenerator extends FabricRecipeProvider {
    public RecipeGenerator(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected @NonNull RecipeProvider createRecipeProvider(HolderLookup.@NonNull Provider registryLookup, @NonNull RecipeOutput exporter) {
        return new RecipeProvider(registryLookup, exporter) {
            final HolderGetter<Item> itemLookup = this.registries.lookupOrThrow(Registries.ITEM);

            @Override
            public void buildRecipes() {
                shaped(RecipeCategory.MISC, Items.NETHERITE_BLOCK)
                        .define('P', Items.PAPER)
                        .define('1', Items.DIAMOND_PICKAXE).define('2', Items.IRON_AXE)
                        .define('3', Items.GOLDEN_HOE).define('4', Items.STONE_SHOVEL)
                        .pattern(" 1 ")
                        .pattern("2P3")
                        .pattern(" 4 ")
                        .unlockedBy("has_paper", inventoryTrigger(ItemPredicate.Builder.item().of(itemLookup, Items.PAPER)))
                        .save(withConditions(output, ResourceConditions.alwaysTrue()), CoolCatLib.rl("testing_recipe").toString());
            }
        };
    }

    @Override
    public @NonNull String getName() {
        return "RecipeGenerator";
    }
}