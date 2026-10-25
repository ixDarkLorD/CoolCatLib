package net.ixdarklord.coolcatcore.api.event.v2.common;

import net.ixdarklord.coolcatcore.api.event.v2.core.EventInvoker;
import net.ixdarklord.coolcatcore.api.event.v2.core.EventResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.jetbrains.annotations.Nullable;

/**
 * Recipes being crafted, on the server.
 */
public final class RecipeEvents {
    public static final EventInvoker<CraftResult> CRAFT_RESULT = EventInvoker.create(CraftResult.class, listeners -> (player, recipe, input, result) -> {
        for (CraftResult listener : listeners) {
            EventResultHolder<ItemStack> outcome = listener.onCraftResult(player, recipe, input, result);
            if (outcome.isInterrupt()) return outcome;
        }
        return EventResultHolder.pass();
    });

    private RecipeEvents() {}

    /**
     * A crafting recipe's result was worked out for the result slot of a crafting grid (the crafting table's, a
     * player's inventory, or another menu built the same way), whenever its ingredients change. Interrupting with
     * another stack shows and crafts that instead; with {@link ItemStack#EMPTY}, nothing can be crafted.
     * <pre>{@code
     * RecipeEvents.CRAFT_RESULT.register((player, recipe, input, result) ->
     *         isLocked(player, recipe) ? EventResultHolder.interrupt(ItemStack.EMPTY) : EventResultHolder.pass());
     * }</pre>
     * This version has no crafter block, so the player is always there (the versions that have one pass null for it),
     * and recipes and crafting grids are this version's own {@link Recipe} and {@link CraftingContainer}.
     *
     * @param player who is crafting (null only for a crafter block, on the versions that have one)
     * @param recipe the recipe, as the game knows it ({@code recipe.getId()} is its id)
     * @param input  the crafting grid
     * @param result what the recipe makes (don't change it; interrupt with another stack instead)
     */
    @FunctionalInterface
    public interface CraftResult {
        EventResultHolder<ItemStack> onCraftResult(@Nullable Player player, Recipe<?> recipe, CraftingContainer input, ItemStack result);
    }
}
