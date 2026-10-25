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
import org.jetbrains.annotations.Nullable;

/**
 * Blocks broken and placed, on the server.
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
}
