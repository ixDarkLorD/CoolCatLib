package net.ixdarklord.coolcatcore.api.event.v2.common;

import net.ixdarklord.coolcatcore.api.event.v2.core.EventInvoker;
import net.ixdarklord.coolcatcore.api.event.v2.core.EventResult;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Players joining and leaving the server, their tick, respawning and changing dimension, picking items up, and crafting
 * and smelting.
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

    public static final EventInvoker<TravelToDimension> TRAVEL_TO_DIMENSION = EventInvoker.create(TravelToDimension.class, listeners -> (player, from, to) -> {
        for (TravelToDimension listener : listeners) {
            EventResult result = listener.onTravelToDimension(player, from, to);
            if (result.isInterrupt()) return result;
        }
        return EventResult.PASS;
    });
    public static final EventInvoker<PickupItem> PICKUP_ITEM = EventInvoker.create(PickupItem.class, listeners -> (player, item) -> {
        for (PickupItem listener : listeners) {
            EventResult result = listener.onPickupItem(player, item);
            if (result.isInterrupt()) return result;
        }
        return EventResult.PASS;
    });
    public static final EventInvoker<Crafted> CRAFTED = EventInvoker.create(Crafted.class, listeners -> (player, crafted, grid) -> {
        for (Crafted listener : listeners) listener.onCrafted(player, crafted, grid);
    });
    public static final EventInvoker<Smelted> SMELTED = EventInvoker.create(Smelted.class, listeners -> (player, smelted, furnace) -> {
        for (Smelted listener : listeners) listener.onSmelted(player, smelted, furnace);
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

    /**
     * A player is about to go to another dimension: by portal, by command, by another mod's teleporter, anything that
     * moves them across. Interrupting keeps them where they are (one standing in a portal isn't asked again for a few
     * seconds). {@link #CHANGE_DIMENSION} follows once they've arrived.
     */
    @FunctionalInterface
    public interface TravelToDimension {
        EventResult onTravelToDimension(ServerPlayer player, ResourceKey<Level> from, ResourceKey<Level> to);
    }

    /**
     * A player is about to pick up an item from the ground: it may be picked up, and they have room. Interrupting leaves
     * it there.
     */
    @FunctionalInterface
    public interface PickupItem {
        EventResult onPickupItem(ServerPlayer player, ItemEntity item);
    }

    /**
     * A player took what they crafted out of a crafting result slot (the crafting table's, their inventory's, or
     * another menu's built the same way), on the server.
     *
     * @param crafted what they took (don't change it)
     * @param grid    the crafting grid it was made from
     */
    @FunctionalInterface
    public interface Crafted {
        void onCrafted(Player player, ItemStack crafted, Container grid);
    }

    /**
     * A player took what was smelted out of a furnace, smoker or blast furnace, on the server.
     *
     * @param smelted what they took (don't change it)
     * @param furnace the furnace's container (its block entity)
     */
    @FunctionalInterface
    public interface Smelted {
        void onSmelted(Player player, ItemStack smelted, Container furnace);
    }

    /** A player went from one dimension to another, by portal or teleport; they are already in the new one. */
    @FunctionalInterface
    public interface ChangeDimension {
        void onChangeDimension(ServerPlayer player, ResourceKey<Level> from, ResourceKey<Level> to);
    }
}
