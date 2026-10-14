package net.ixdarklord.coolcatcore.api.event.v2.common;

import net.ixdarklord.coolcatcore.api.event.v2.core.EventInvoker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Players joining and leaving the server, and their tick.
 */
public final class PlayerEvents {
    public static final EventInvoker<Join> JOIN = EventInvoker.create(Join.class, listeners -> player -> {
        for (Join listener : listeners) listener.onJoin(player);
    });
    public static final EventInvoker<Leave> LEAVE = EventInvoker.create(Leave.class, listeners -> player -> {
        for (Leave listener : listeners) listener.onLeave(player);
    });
    public static final EventInvoker<StartTick> START_TICK = EventInvoker.create(StartTick.class, listeners -> player -> {
        for (StartTick listener : listeners) listener.onStartTick(player);
    });
    public static final EventInvoker<EndTick> END_TICK = EventInvoker.create(EndTick.class, listeners -> player -> {
        for (EndTick listener : listeners) listener.onEndTick(player);
    });

    private PlayerEvents() {}

    /** A player finished logging in and is in the level. */
    @FunctionalInterface
    public interface Join {
        void onJoin(ServerPlayer player);
    }

    /** A player is logging out. */
    @FunctionalInterface
    public interface Leave {
        void onLeave(ServerPlayer player);
    }

    /** Before a player ticks, on both sides; check {@code player.level().isClientSide()}. */
    @FunctionalInterface
    public interface StartTick {
        void onStartTick(Player player);
    }

    /** After a player ticks, on both sides; check {@code player.level().isClientSide()}. */
    @FunctionalInterface
    public interface EndTick {
        void onEndTick(Player player);
    }
}
