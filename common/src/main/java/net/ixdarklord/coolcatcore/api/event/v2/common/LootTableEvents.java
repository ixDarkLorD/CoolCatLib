package net.ixdarklord.coolcatcore.api.event.v2.common;

import net.ixdarklord.coolcatcore.api.event.v2.core.EventInvoker;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.function.Consumer;

/**
 * Loot tables as they load (on server start and on {@code /reload}), vanilla's, mods' and data packs' alike.
 * <ul>
 *     <li>{@link #MODIFY}: add pools to a loot table, such as a rare extra item in a structure's chests. NeoForge's
 *     {@code LootTableLoadEvent} and Fabric's {@code LootTableEvents.MODIFY}.</li>
 * </ul>
 * <pre>{@code
 * LootTableEvents.MODIFY.register((table, pools) -> {
 *     if (table.equals(BuiltInLootTables.ANCIENT_CITY)) pools.accept(LootPool.lootPool()
 *             .when(LootItemRandomChanceCondition.randomChance(0.05F))
 *             .add(LootItem.lootTableItem(Items.ECHO_SHARD)).build());
 * });
 * }</pre>
 */
public final class LootTableEvents {
    public static final EventInvoker<Modify> MODIFY = EventInvoker.create(Modify.class, listeners -> (table, pools) -> {
        for (Modify listener : listeners) listener.modify(table, pools);
    });

    private LootTableEvents() {}

    /** A loot table loading, to add pools to. */
    @FunctionalInterface
    public interface Modify {
        /**
         * @param table the loot table's id
         * @param pools adds a pool to it
         */
        void modify(ResourceKey<LootTable> table, Consumer<LootPool> pools);
    }
}
