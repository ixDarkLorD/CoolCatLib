package net.ixdarklord.coolcatcore.api.event.v2.common;

import net.ixdarklord.coolcatcore.api.event.v2.core.EventInvoker;
import net.ixdarklord.coolcatcore.api.event.v2.core.EventResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import java.util.List;

/**
 * Enchanting at an enchanting table, and combining at an anvil, on the server.
 */
public final class EnchantEvents {
    public static final EventInvoker<TableOptions> TABLE_OPTIONS = EventInvoker.create(TableOptions.class, listeners -> (player, item, slot, enchantments) -> {
        for (TableOptions listener : listeners) listener.onTableOptions(player, item, slot, enchantments);
    });
    public static final EventInvoker<AnvilResult> ANVIL_RESULT = EventInvoker.create(AnvilResult.class, listeners -> (player, left, right, result) -> {
        for (AnvilResult listener : listeners) {
            EventResultHolder<ItemStack> outcome = listener.onAnvilResult(player, left, right, result);
            if (outcome.isInterrupt()) return outcome;
        }
        return EventResultHolder.pass();
    });

    private EnchantEvents() {}

    /**
     * The enchantments an enchanting table would give an item for one of its three offers, worked out both to show the
     * hint and when the offer is taken. Take enchantments out of the list (or put others in) to change what it gives;
     * an emptied list gives nothing, and the offer can't be taken. Called again with the same random choice each time,
     * so change it the same way each time.
     *
     * @param slot          which offer: 0, 1 or 2
     * @param enchantments  what it would give (change this list)
     */
    @FunctionalInterface
    public interface TableOptions {
        void onTableOptions(Player player, ItemStack item, int slot, List<EnchantmentInstance> enchantments);
    }

    /**
     * An anvil worked out what its two inputs make (renamed, repaired, enchanted or combined). Interrupting with another
     * stack shows and gives that instead, with {@link ItemStack#EMPTY} nothing can be taken; the cost stays the same.
     *
     * @param result what it makes (don't change it; interrupt with another stack instead)
     */
    @FunctionalInterface
    public interface AnvilResult {
        EventResultHolder<ItemStack> onAnvilResult(Player player, ItemStack left, ItemStack right, ItemStack result);
    }
}
