package net.ixdarklord.coolcatcore.internal.event;

import net.ixdarklord.coolcatcore.api.event.v2.common.LootEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * LootEvents.MODIFY: a table rolled with a new context collects its loot (LootTableMixin), told about once it's done
 * and then passed on as the game would have. Tables rolled inside it share its context and are part of its loot. A
 * table's id is looked up among the loaded tables the first time it's rolled (LootDataManagerMixin), then remembered.
 */
public final class LootRolls {
    private static final Map<LootTable, ResourceLocation> IDS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Deque<Roll>> ROLLS = ThreadLocal.withInitial(ArrayDeque::new);

    private LootRolls() {}

    private static @Nullable ResourceLocation idOf(LootTable table, LootContext context) {
        ResourceLocation id = IDS.get(table);
        if (id != null || table == LootTable.EMPTY) return id;
        id = ((Ids) context.getLevel().getServer().getLootData()).coolcatlib$idOf(table);
        if (id != null) IDS.put(table, id);
        return id;
    }

    /** The output a roll starting now writes to: its own list when it's a new roll that's listened to. */
    public static Consumer<ItemStack> begin(LootTable table, LootContext context, Consumer<ItemStack> output) {
        Deque<Roll> rolls = ROLLS.get();
        if (rolls.size() > 64) rolls.clear(); // (only rolls that threw are left over)
        for (Roll roll : rolls) if (roll.context == context) return output;
        if (!LootEvents.MODIFY.hasListeners()) return output;
        Roll roll = new Roll(table, context, output, new ArrayList<>());
        rolls.push(roll);
        return roll.loot::add;
    }

    public static void end(LootTable table, LootContext context) {
        Deque<Roll> rolls = ROLLS.get();
        Roll roll = rolls.peek();
        if (roll == null || roll.context != context || roll.table != table) return;
        rolls.pop();
        LootEvents.MODIFY.invoker().onModify(idOf(table, context), context, roll.loot);
        for (ItemStack stack : roll.loot) if (!stack.isEmpty()) roll.output.accept(stack);
    }

    private record Roll(LootTable table, LootContext context, Consumer<ItemStack> output, List<ItemStack> loot) {}

    /** Implemented by LootDataManager: a loaded table's id. */
    public interface Ids {
        @Nullable ResourceLocation coolcatlib$idOf(LootTable table);
    }
}
