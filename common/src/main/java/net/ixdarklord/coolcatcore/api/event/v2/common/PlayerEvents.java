package net.ixdarklord.coolcatcore.api.event.v2.common;

import net.ixdarklord.coolcatcore.api.event.v2.core.EventInvoker;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Players joining and leaving the server, their tick, respawning and changing dimension.
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
    public static final EventInvoker<Clone> CLONE = EventInvoker.create(Clone.class, listeners -> (original, player, died) -> {
        for (Clone listener : listeners) listener.onClone(original, player, died);
    });
    public static final EventInvoker<Respawn> RESPAWN = EventInvoker.create(Respawn.class, listeners -> (player, fromEnd) -> {
        for (Respawn listener : listeners) listener.onRespawn(player, fromEnd);
    });
    public static final EventInvoker<ChangeDimension> CHANGE_DIMENSION = EventInvoker.create(ChangeDimension.class, listeners -> (player, from, to) -> {
        for (ChangeDimension listener : listeners) listener.onChangeDimension(player, from, to);
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

    /**
     * The game made a new player object for a player who died or came back from the End, and is copying them over:
     * the place to carry over what the game doesn't (attachments kept through death already are).
     *
     * @param died true after death, false when coming back from the End
     */
    @FunctionalInterface
    public interface Clone {
        void onClone(ServerPlayer original, ServerPlayer player, boolean died);
    }

    /**
     * A player is back in the world after dying or leaving the End (the new player object).
     *
     * @param fromEnd true when coming back from the End, false after death
     */
    @FunctionalInterface
    public interface Respawn {
        void onRespawn(ServerPlayer player, boolean fromEnd);
    }

    /** A player went from one dimension to another, by portal or teleport; they are already in the new one. */
    @FunctionalInterface
    public interface ChangeDimension {
        void onChangeDimension(ServerPlayer player, ResourceKey<Level> from, ResourceKey<Level> to);
    }
}
