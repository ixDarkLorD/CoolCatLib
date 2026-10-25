package net.ixdarklord.coolcatcore.api.event.v2.common;

import net.ixdarklord.coolcatcore.api.event.v2.core.EventInvoker;
import net.ixdarklord.coolcatcore.api.event.v2.core.EventResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
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
     * A crafting recipe's result was worked out: for the result slot of a crafting grid (the crafting table's, a
     * player's inventory, or another menu built the same way) whenever its ingredients change, or for a crafter about to
     * craft. Interrupting with another stack shows and crafts that instead; with {@link ItemStack#EMPTY}, nothing can be
     * crafted (a crafter fails as it does with no recipe).
     * <pre>{@code
     * RecipeEvents.CRAFT_RESULT.register((player, recipe, input, result) ->
     *         isLocked(player, recipe) ? EventResultHolder.interrupt(ItemStack.EMPTY) : EventResultHolder.pass());
     * }</pre>
     *
     * @param player who is crafting, or null for a crafter block
     * @param result what the recipe makes (don't change it; interrupt with another stack instead)
     */
    @FunctionalInterface
    public interface CraftResult {
        EventResultHolder<ItemStack> onCraftResult(@Nullable Player player, RecipeHolder<?> recipe, CraftingInput input, ItemStack result);
    }
}
