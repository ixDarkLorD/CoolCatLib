package net.ixdarklord.coolcatcore.api.event.v2.common;

import net.ixdarklord.coolcatcore.api.event.v2.core.EventInvoker;
import net.ixdarklord.coolcatcore.api.event.v2.core.EventResult;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
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
    public static final EventInvoker<Placed> PLACED = EventInvoker.create(Placed.class, listeners -> (level, pos, state, placer) -> {
        for (Placed listener : listeners) listener.onPlaced(level, pos, state, placer);
    });

    private BlockEvents() {}

    /** A player is about to break a block (server side); interrupting cancels it. */
    @FunctionalInterface
    public interface Break {
        EventResult onBreak(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player);
    }

    /** A block was placed: by an entity (usually a player), or with no placer. */
    @FunctionalInterface
    public interface Placed {
        void onPlaced(Level level, BlockPos pos, BlockState state, @Nullable Entity placer);
    }
}
