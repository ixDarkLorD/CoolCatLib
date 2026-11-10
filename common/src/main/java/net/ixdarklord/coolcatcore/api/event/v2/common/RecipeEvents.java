package net.ixdarklord.coolcatcore.api.event.v2.common;

import net.ixdarklord.coolcatcore.api.event.v2.core.EventInvoker;
import net.ixdarklord.coolcatcore.api.event.v2.core.EventResult;
import net.ixdarklord.coolcatcore.api.event.v2.core.EventResultHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Recipes being crafted, on the server.
 */
public final class RecipeEvents {
    public static final EventInvoker<Match> MATCH = EventInvoker.create(Match.class, listeners -> (level, recipe, blockEntity, player) -> {
        for (Match listener : listeners) {
            EventResult result = listener.onMatch(level, recipe, blockEntity, player);
            if (result.isInterrupt()) return result;
        }
        return EventResult.PASS;
    });
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

    /**
     * A recipe matched what was put in, wherever the game looks one up: a furnace, smoker or blast furnace, a campfire, a
     * smithing table, a stonecutter, a crafting grid, or another mod's machine that asks the game for a recipe.
     * Interrupting skips this recipe: the next one that matches is used, or none (the furnace doesn't cook, the result
     * slot stays empty). A crafter's recipes go through {@link #CRAFT_RESULT} instead. It's asked often (a burning
     * furnace, every tick), so keep it quick.
     * <pre>{@code
     * RecipeEvents.MATCH.register((level, recipe, blockEntity, player) -> {
     *     UUID owner = blockEntity != null ? ownerOf(blockEntity) : player != null ? player.getUUID() : null;
     *     return owner != null && isLocked(owner, recipe) ? EventResult.INTERRUPT : EventResult.PASS;
     * });
     * }</pre>
     *
     * @param recipe      the recipe that matched
     * @param blockEntity the machine looking it up (a furnace, a campfire), or null
     * @param player      the player using it (at a crafting grid, a smithing table or a stonecutter, or putting food on a
     *                    campfire), or null
     */
    @FunctionalInterface
    public interface Match {
        EventResult onMatch(ServerLevel level, RecipeHolder<?> recipe, @Nullable BlockEntity blockEntity, @Nullable Player player);
    }
}
