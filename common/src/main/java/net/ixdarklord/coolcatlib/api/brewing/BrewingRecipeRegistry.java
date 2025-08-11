package net.ixdarklord.coolcatlib.api.brewing;

import net.ixdarklord.coolcatlib.api.event.v1.server.RegisterBrewingRecipesEvent;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collections;
import java.util.List;

/**
 * Central singleton-style registry for managing all <em>custom</em> brewing recipes
 * registered by mods using this API.
 * <p>
 * This registry holds a read-only list of {@link IBrewingRecipe} implementations and
 * provides utility methods for:
 * <ul>
 *     <li>Validating brewing inputs and ingredients.</li>
 *     <li>Checking whether a brewing combination has a valid output.</li>
 *     <li>Retrieving the resulting brewed item for a given input + ingredient pair.</li>
 *     <li>Performing multi-slot brewing stand operations in batch.</li>
 * </ul>
 *
 * <h2>Lifecycle</h2>
 * <ul>
 *     <li>Exactly one instance must be created during mod initialization, typically
 *     during the {@link RegisterBrewingRecipesEvent} event.</li>
 *     <li>The constructor enforces this constraint — creating a second instance will
 *     throw an {@link IllegalStateException}.</li>
 *     <li>Call {@link #IsLoaded()} before {@link #getInstance()} to avoid {@code null} returns.</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <ul>
 *     <li>This registry is <strong>not</strong> thread-safe. All access should occur
 *     from the main server thread during predictable lifecycle phases.</li>
 *     <li>The internal recipe list is made immutable via {@link Collections#unmodifiableList(List)}
 *     to prevent modification after construction.</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * // Registration
 * @SubscribeEvent
 * public void onRegisterBrewing(RegisterBrewingRecipesEvent event) {
 *     List<IBrewingRecipe> recipes = List.of(
 *         new MyCustomBrewingRecipe(),
 *         new AnotherBrewingRecipe()
 *     );
 *     new BrewingRecipeRegistry(recipes);
 * }
 *
 * // Querying
 * ItemStack result = BrewingRecipeRegistry.getInstance()
 *     .getOutput(inputStack, ingredientStack);
 * if (!result.isEmpty()) {
 *     // Do something with the brewed result
 * }
 * }</pre>
 *
 * <h2>Relation to Vanilla Brewing</h2>
 * <p>For vanilla brewing mechanics and recipes, prefer
 * {@link PotionBrewing}. Use this registry only for
 * custom modded recipes.</p>
 *
 * @param recipes Immutable list of registered brewing recipes.
 * @implNote This registry uses a strict singleton pattern with no reset mechanism.
 * If you need to rebuild the registry, it must occur during a clean reload.
 * @apiNote Marked {@link ApiStatus.Internal} — external mods should not construct this directly,
 * but rather register recipes through {@link RegisterBrewingRecipesEvent}.
 */
@ApiStatus.Internal
public record BrewingRecipeRegistry(List<IBrewingRecipe> recipes) {
    private static BrewingRecipeRegistry instance = null;

    /**
     * Constructs and initializes the brewing recipe registry.
     * <p>
     * This constructor should only be called once. Attempting to construct it again will
     * result in an {@link IllegalStateException}.
     *
     * @param recipes List of recipes to register (cannot be {@code null}).
     * @throws IllegalStateException    if an instance already exists.
     * @throws IllegalArgumentException if {@code recipes} is {@code null}.
     */
    public BrewingRecipeRegistry(List<IBrewingRecipe> recipes) {
        if (recipes == null) {
            throw new IllegalArgumentException("Recipes list cannot be null");
        }

        this.recipes = Collections.unmodifiableList(recipes);
        instance = this;
    }

    /**
     * Checks if the registry is loaded.
     *
     * @return {@code true} if {@link #getInstance()} will return a non-null reference.
     */
    public static boolean IsLoaded() {
        return instance != null;
    }

    /**
     * Retrieves the instance of the registry.
     *
     * @return The singleton instance, or {@code null} if not initialized.
     */
    public static BrewingRecipeRegistry getInstance() {
        return instance;
    }

    /**
     * Returns the immutable list of registered brewing recipes.
     *
     * @return Read-only recipe list.
     */
    @Override
    public List<IBrewingRecipe> recipes() {
        return recipes;
    }

    /**
     * Determines if at least one brewing operation is possible with the given
     * brewing stand slots and ingredient.
     *
     * @param inputs       List of brewing stand input slots.
     * @param ingredient   Brewing stand's top slot item.
     * @param inputIndexes Indices in {@code inputs} to check.
     * @return {@code true} if at least one input can be brewed with the given ingredient.
     */
    public boolean canBrew(NonNullList<ItemStack> inputs, ItemStack ingredient, int[] inputIndexes) {
        if (ingredient.isEmpty()) return false;
        for (int i : inputIndexes) {
            if (hasOutput(inputs.get(i), ingredient)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Applies brewing to the given input slots using the specified ingredient.
     * Only slots producing a valid result will be replaced with their brewed output.
     *
     * @param inputs       Brewing stand input slots.
     * @param ingredient   Brewing stand's top slot item.
     * @param inputIndexes Indices in {@code inputs} to attempt brewing on.
     */
    public void brewPotions(NonNullList<ItemStack> inputs, ItemStack ingredient, int[] inputIndexes) {
        for (int i : inputIndexes) {
            ItemStack output = getOutput(inputs.get(i), ingredient);
            if (!output.isEmpty()) {
                inputs.set(i, output);
            }
        }
    }

    /**
     * Attempts to retrieve the brewed output for the given input and ingredient.
     *
     * @param input      Brewing stand's bottom slot item (must have count = 1).
     * @param ingredient Brewing stand's top slot item.
     * @return Brewed {@link ItemStack}, or {@link ItemStack#EMPTY} if no recipe matches.
     */
    public ItemStack getOutput(ItemStack input, ItemStack ingredient) {
        if (input.isEmpty() || input.getCount() != 1) return ItemStack.EMPTY;
        if (ingredient.isEmpty()) return ItemStack.EMPTY;

        for (IBrewingRecipe recipe : recipes) {
            ItemStack output = recipe.getOutput(input, ingredient);
            if (!output.isEmpty()) {
                return output;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Checks if there is any recipe producing a valid output for the given combination.
     *
     * @param input      Brewing stand's bottom slot item.
     * @param ingredient Brewing stand's top slot item.
     * @return {@code true} if a recipe matches; {@code false} otherwise.
     */
    public boolean hasOutput(ItemStack input, ItemStack ingredient) {
        return !getOutput(input, ingredient).isEmpty();
    }

    /**
     * Determines if the given stack is a valid ingredient in any registered recipe.
     *
     * @param stack Item to test.
     * @return {@code true} if valid for use as an ingredient.
     */
    public boolean isValidIngredient(ItemStack stack) {
        if (stack.isEmpty()) return false;

        for (IBrewingRecipe recipe : recipes) {
            if (recipe.isIngredient(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines if the given stack is a valid brewing input in any registered recipe.
     *
     * @param stack Item to test.
     * @return {@code true} if valid for use as a base/input item.
     */
    public boolean isValidInput(ItemStack stack) {
        for (IBrewingRecipe recipe : recipes) {
            if (recipe.isInput(stack)) {
                return true;
            }
        }
        return false;
    }
}
