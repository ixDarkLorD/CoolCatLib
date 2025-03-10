package net.ixdarklord.coolcatlib.api.brewing;

import com.google.common.collect.Lists;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class BrewingBuilder extends PotionBrewing.Builder {
    private final PotionBrewing.Builder builder;
    private final List<IBrewingRecipe> brewingRecipes = Lists.newArrayList();

    public BrewingBuilder(PotionBrewing.Builder builder) {
        super(null);
        this.builder = builder;
    }

    public void addRecipe(Ingredient input, Ingredient ingredient, ItemStack output) {
        this.addRecipe(new BrewingRecipe(input, ingredient, output));
    }

    public void addRecipe(IBrewingRecipe recipe) {
        this.brewingRecipes.add(recipe);
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

    public List<IBrewingRecipe> getBrewingRecipes() {
        return brewingRecipes;
    }
}
