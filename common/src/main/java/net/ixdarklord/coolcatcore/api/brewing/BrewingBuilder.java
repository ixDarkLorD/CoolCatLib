package net.ixdarklord.coolcatcore.api.brewing;

import com.google.common.collect.Lists;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Brewing recipes made in code, alongside the game's own (data pack recipes since 26.3). Mixes and container recipes
 * work as vanilla's did: a potion in any container brews into another potion in the same container, and a container
 * brews into another holding the same potion.
 */
public final class BrewingBuilder {
    private final List<IBrewingRecipe> brewingRecipes = Lists.newArrayList();
    private final Set<Item> containers = new LinkedHashSet<>(List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION));

    public void addRecipe(Ingredient input, Ingredient ingredient, ItemStack output) {
        this.addRecipe(new BrewingRecipe(input, ingredient, output));
    }

    public void addRecipe(IBrewingRecipe recipe) {
        this.brewingRecipes.add(recipe);
    }

    /** An item potions can be brewed in by the mixes added here (potions, splash and lingering potions already are). */
    public void addContainer(Item container) {
        this.containers.add(container);
    }

    /** A potion in {@code input} brewed with {@code reagent} becomes the same potion in {@code result}. */
    public void addContainerRecipe(Item input, Item reagent, Item result) {
        this.addRecipe(new Converted(input, Ingredient.of(reagent), result));
    }

    /** {@code input} brewed with {@code reagent} becomes {@code result}, in whichever container it is in. */
    public void addMix(Holder<Potion> input, Item reagent, Holder<Potion> result) {
        this.addRecipe(new Mix(this.containers, input, Ingredient.of(reagent), result));
    }

    /** An awkward potion brewed with {@code reagent} becomes {@code result}, as vanilla's own start mixes do. */
    public void addStartMix(Item reagent, Holder<Potion> result) {
        this.addMix(Potions.AWKWARD, reagent, result);
    }

    public List<IBrewingRecipe> getBrewingRecipes() {
        return this.brewingRecipes;
    }

    private static Optional<Holder<Potion>> potion(ItemStack stack) {
        return stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).potion();
    }

    // A potion mix. The containers are read while brewing, so ones added after the mix count too.
    private record Mix(Set<Item> containers, Holder<Potion> from, Ingredient reagent, Holder<Potion> to) implements IBrewingRecipe {
        @Override
        public boolean isInput(ItemStack input) {
            return this.containers.contains(input.getItem()) && potion(input).filter(this.from::equals).isPresent();
        }

        @Override
        public boolean isIngredient(ItemStack ingredient) {
            return this.reagent.test(ingredient);
        }

        @Override
        public @NotNull Ingredient input() {
            return Ingredient.of(this.containers.stream());
        }

        @Override
        public @NotNull Ingredient ingredient() {
            return this.reagent;
        }

        @Override
        public @NotNull ItemStack output() {
            return PotionContents.createItemStack(Items.POTION, this.to);
        }

        @Override
        public @NotNull ItemStack getOutput(ItemStack input, ItemStack ingredient) {
            if (!this.isInput(input) || !this.isIngredient(ingredient)) return ItemStack.EMPTY;
            ItemStack result = input.copyWithCount(1);
            result.set(DataComponents.POTION_CONTENTS, new PotionContents(this.to));
            return result;
        }
    }

    // A container recipe: the potion stays, its container changes.
    private record Converted(Item from, Ingredient reagent, Item to) implements IBrewingRecipe {
        @Override
        public boolean isInput(ItemStack input) {
            return input.is(this.from);
        }

        @Override
        public boolean isIngredient(ItemStack ingredient) {
            return this.reagent.test(ingredient);
        }

        @Override
        public @NotNull Ingredient input() {
            return Ingredient.of(this.from);
        }

        @Override
        public @NotNull Ingredient ingredient() {
            return this.reagent;
        }

        @Override
        public @NotNull ItemStack output() {
            return new ItemStack(this.to);
        }

        @Override
        public @NotNull ItemStack getOutput(ItemStack input, ItemStack ingredient) {
            if (!this.isInput(input) || !this.isIngredient(ingredient)) return ItemStack.EMPTY;
            return input.transmuteCopy(this.to, 1);
        }
    }
}
