package net.ixdarklord.coolcatlib.api.brewing.fabric;

import net.ixdarklord.coolcatlib.api.brewing.fabric.ext.PotionBrewingBuilderExt;
import net.minecraft.core.Holder;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;

public final class BrewingBuilder extends PotionBrewing.Builder implements PotionBrewingBuilderExt {
    private final PotionBrewing.Builder builder;

    public BrewingBuilder(PotionBrewing.Builder builder) {
        super(builder.getEnabledFeatures());
        this.builder = builder;
    }

    @Override
    public void addRecipe(Ingredient input, Ingredient ingredient, ItemStack output) {
        ((PotionBrewingBuilderExt) builder).addRecipe(input, ingredient, output);
    }

    @Override
    public void addRecipe(IBrewingRecipe recipe) {
        ((PotionBrewingBuilderExt) builder).addRecipe(recipe);
    }

    @Override
    public void addContainerRecipe(Item input, Item reagent, Item result) {
        this.builder.addContainerRecipe(input, reagent, result);
    }

    @Override
    public void addContainer(Item container) {
        this.builder.addContainer(container);
    }

    @Override
    public void addMix(Holder<Potion> input, Item reagent, Holder<Potion> result) {
        this.builder.addMix(input, reagent, result);
    }

    @Override
    public void addStartMix(Item reagent, Holder<Potion> result) {
        this.builder.addStartMix(reagent, result);
    }

    @Override
    public @NotNull PotionBrewing build() {
        return this.builder.build();
    }

    @Override
    public void registerItemRecipe(Item input, Ingredient ingredient, Item output) {
        this.builder.registerItemRecipe(input, ingredient, output);
    }

    @Override
    public void registerPotionRecipe(Holder<Potion> input, Ingredient ingredient, Holder<Potion> output) {
        this.builder.registerPotionRecipe(input, ingredient, output);
    }

    @Override
    public void registerRecipes(Ingredient ingredient, Holder<Potion> potion) {
        this.builder.registerRecipes(ingredient, potion);
    }

    @Override
    public FeatureFlagSet getEnabledFeatures() {
        return this.builder.getEnabledFeatures();
    }
}
