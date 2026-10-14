package net.ixdarklord.coolcatcore.api.brewing;

import com.google.common.collect.Lists;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Collects brewing recipes while {@link net.ixdarklord.coolcatcore.api.event.v1.server.RegisterBrewingRecipesEvent}
 * fires. Custom recipes ({@link #addRecipe}) are handed to the loader; the vanilla kinds ({@link #addMix},
 * {@link #addContainerRecipe}...) go straight into 1.20.1's global {@link PotionBrewing} tables.
 */
public final class BrewingBuilder {
    private final List<IBrewingRecipe> brewingRecipes = Lists.newArrayList();

    public BrewingBuilder() {
    }

    public void addRecipe(@NotNull Ingredient input, @NotNull Ingredient ingredient, @NotNull ItemStack output) {
        this.addRecipe(new BrewingRecipe(input, ingredient, output));
    }

    public void addRecipe(@NotNull IBrewingRecipe recipe) {
        this.brewingRecipes.add(recipe);
    }

    /** Brewing {@code reagent} into a potion held in {@code input} turns its container into {@code result}. */
    public void addContainerRecipe(@NotNull Item input, @NotNull Item reagent, @NotNull Item result) {
        PotionBrewing.addContainerRecipe(input, reagent, result);
    }

    /** Lets potions in {@code container} be brewed. */
    public void addContainer(@NotNull Item container) {
        PotionBrewing.addContainer(container);
    }

    /** Brewing {@code reagent} into an {@code input} potion gives a {@code result} potion. */
    public void addMix(@NotNull Potion input, @NotNull Item reagent, @NotNull Potion result) {
        PotionBrewing.addMix(input, reagent, result);
    }

    /** Brewing {@code reagent} into an awkward potion gives {@code result} (and into water, a mundane potion). */
    public void addStartMix(@NotNull Item reagent, @NotNull Potion result) {
        this.addMix(Potions.WATER, reagent, Potions.MUNDANE);
        this.addMix(Potions.AWKWARD, reagent, result);
    }

    /** The custom recipes added so far, as a registry to query. */
    public @NotNull BrewingRecipeRegistry build() {
        return new BrewingRecipeRegistry(List.copyOf(this.brewingRecipes));
    }

    public List<IBrewingRecipe> getBrewingRecipes() {
        return this.brewingRecipes;
    }
}
