package net.ixdarklord.coolcatcore.api.event.v2.common;

import net.ixdarklord.coolcatcore.api.event.v2.core.EventInvoker;
import net.ixdarklord.coolcatcore.api.event.v2.core.EventResult;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Blocks broken and placed, and what they drop, on the server.
 */
public final class BlockEvents {
    public static final EventInvoker<Break> BREAK = EventInvoker.create(Break.class, listeners -> (level, pos, state, player) -> {
        for (Break listener : listeners) {
            EventResult result = listener.onBreak(level, pos, state, player);
            if (result.isInterrupt()) return result;
        }
        return EventResult.PASS;
    });
    public static final EventInvoker<Place> PLACE = EventInvoker.create(Place.class, listeners -> (level, pos, state, placer, stack) -> {
        for (Place listener : listeners) {
            EventResult result = listener.onPlace(level, pos, state, placer, stack);
            if (result.isInterrupt()) return result;
        }
        return EventResult.PASS;
    });
    public static final EventInvoker<Placed> PLACED = EventInvoker.create(Placed.class, listeners -> (level, pos, state, placer) -> {
        for (Placed listener : listeners) listener.onPlaced(level, pos, state, placer);
    });
    public static final EventInvoker<Drops> DROPS = EventInvoker.create(Drops.class, listeners -> (level, pos, state, breaker, tool, drops, experience) -> {
        for (Drops listener : listeners) listener.onDrops(level, pos, state, breaker, tool, drops, experience);
    });

    private BlockEvents() {}

    /** A player is about to break a block (server side); interrupting cancels it. */
    @FunctionalInterface
    public interface Break {
        EventResult onBreak(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player);
    }

    /**
     * A block is about to be placed, on the server; interrupting stops it, and the item isn't used up. Fired for block
     * items, whoever uses them (players, dispensers, other mods' machines), and for endermen setting down the block
     * they carry. {@link #PLACED} follows once it is in the level.
     *
     * @param state  the block it would place
     * @param placer who places it, or null for a dispenser or another machine
     * @param stack  the item placing it, or {@link ItemStack#EMPTY} for an enderman
     */
    @FunctionalInterface
    public interface Place {
        EventResult onPlace(Level level, BlockPos pos, BlockState state, @Nullable Entity placer, ItemStack stack);
    }

    /** A block was placed: by an entity (usually a player), or with no placer. */
    @FunctionalInterface
    public interface Placed {
        void onPlaced(Level level, BlockPos pos, BlockState state, @Nullable Entity placer);
    }

    /**
     * A block broken by a player, a machine, a piston or water is about to drop its items and experience. Change the
     * list to change what it drops, and {@code experience} to change how much it gives. Blocks blown up drop through
     * the explosion instead, and don't fire it.
     * <pre>{@code
     * BlockEvents.DROPS.register((level, pos, state, breaker, tool, drops, experience) -> {
     *     if (state.is(Blocks.DIAMOND_ORE) && isLocked(breaker)) {
     *         drops.clear();
     *         drops.add(new ItemStack(Items.COBBLESTONE));
     *         experience.setValue(0);
     *     }
     * });
     * }</pre>
     *
     * @param state      the block as it was
     * @param breaker    who broke it (a player, an entity), or null
     * @param tool       what it was broken with, or {@link ItemStack#EMPTY}
     * @param drops      the items it drops (change this list)
     * @param experience the experience it drops (change it)
     */
    @FunctionalInterface
    public interface Drops {
        void onDrops(ServerLevel level, BlockPos pos, BlockState state, @Nullable Entity breaker, ItemStack tool, List<ItemStack> drops, MutableInt experience);
    }
}
