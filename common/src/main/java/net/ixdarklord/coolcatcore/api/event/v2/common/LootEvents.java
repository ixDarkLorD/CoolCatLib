package net.ixdarklord.coolcatcore.api.event.v2.common;

import net.ixdarklord.coolcatcore.api.event.v2.core.EventInvoker;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Loot tables rolled on the server.
 */
public final class LootEvents {
    public static final EventInvoker<Modify> MODIFY = EventInvoker.create(Modify.class, listeners -> (table, context, loot) -> {
        for (Modify listener : listeners) listener.onModify(table, context, loot);
    });

    private LootEvents() {}

    /**
     * A loot table was rolled: a chest's, a block's drops, a mob's, fishing, an archaeology find, a vault's, and so on.
     * Change the list to change the loot (take stacks out, put others in); the stacks in it may be bigger than they
     * stack, and are split up after. Tables rolled as part of another table (a reference to it) are part of that one's
     * loot, and don't fire it themselves.
     * <pre>{@code
     * LootEvents.MODIFY.register((table, context, loot) -> {
     *     if (table != null && table.location().getPath().startsWith("chests/")) loot.removeIf(stack -> isLocked(stack));
     * });
     * }</pre>
     *
     * @param table   which table, or null when it wasn't looked up by its id (one built in code)
     * @param context what it was rolled with: the level, and the parameters the table had (the entity killed, the
     *                player, the tool, the position)
     */
    @FunctionalInterface
    public interface Modify {
        void onModify(@Nullable ResourceKey<LootTable> table, LootContext context, List<ItemStack> loot);
    }
}
