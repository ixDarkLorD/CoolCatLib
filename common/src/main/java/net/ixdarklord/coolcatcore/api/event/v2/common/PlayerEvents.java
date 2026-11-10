package net.ixdarklord.coolcatcore.api.event.v2.common;

import net.ixdarklord.coolcatcore.api.event.v2.core.EventInvoker;
import net.ixdarklord.coolcatcore.api.event.v2.core.EventResult;
import net.ixdarklord.coolcatcore.api.event.v2.core.EventResultHolder;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
    public static final EventInvoker<OpenMenu> OPEN_MENU = EventInvoker.create(OpenMenu.class, listeners -> (player, menu) -> {
        for (OpenMenu listener : listeners) {
            EventResult result = listener.onOpenMenu(player, menu);
            if (result.isInterrupt()) return result;
        }
        return EventResult.PASS;
    });
    public static final EventInvoker<AdvancementEarned> ADVANCEMENT_EARNED = EventInvoker.create(AdvancementEarned.class, listeners -> (player, advancement) -> {
        for (AdvancementEarned listener : listeners) listener.onAdvancementEarned(player, advancement);
    });
    public static final EventInvoker<BreakSpeed> BREAK_SPEED = EventInvoker.create(BreakSpeed.class, listeners -> (player, state, pos, speed) -> {
        float current = speed;
        boolean changed = false;
        for (BreakSpeed listener : listeners) {
            EventResultHolder<Float> outcome = listener.onBreakSpeed(player, state, pos, current);
            if (outcome.isInterrupt()) {
                current = outcome.getInterrupt().orElse(current);
                changed = true;
            }
        }
        return changed ? EventResultHolder.interrupt(current) : EventResultHolder.pass();
    });
    public static final EventInvoker<HarvestCheck> HARVEST_CHECK = EventInvoker.create(HarvestCheck.class, listeners -> (player, state, pos, canHarvest) -> {
        for (HarvestCheck listener : listeners) {
            EventResultHolder<Boolean> outcome = listener.onHarvestCheck(player, state, pos, canHarvest);
            if (outcome.isInterrupt()) return outcome;
        }
        return EventResultHolder.pass();
    });
    public static final EventInvoker<Crafted> CRAFTED = EventInvoker.create(Crafted.class, listeners -> (player, crafted, grid) -> {
        for (Crafted listener : listeners) listener.onCrafted(player, crafted, grid);
    });
    public static final EventInvoker<Smelted> SMELTED = EventInvoker.create(Smelted.class, listeners -> (player, smelted, furnace) -> {
        for (Smelted listener : listeners) listener.onSmelted(player, smelted, furnace);
    });
    public static final EventInvoker<ItemFished> ITEM_FISHED = EventInvoker.create(ItemFished.class, listeners -> (player, hook, items) -> {
        for (ItemFished listener : listeners) listener.onItemFished(player, hook, items);
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

    /**
     * A player is about to open a menu: a block's, an entity's (a villager's trades, a chest boat), an item's, or one a
     * command or another mod opens. Interrupting keeps it closed. A horse's inventory isn't opened this way and doesn't
     * fire it.
     *
     * @param menu what makes the menu: a block entity, an entity, or a {@link net.minecraft.world.SimpleMenuProvider}
     */
    @FunctionalInterface
    public interface OpenMenu {
        EventResult onOpenMenu(ServerPlayer player, MenuProvider menu);
    }

    /**
     * A player completed an advancement (its last criterion was just met, and its rewards given), on the server. Not
     * fired again for one they had already completed.
     */
    @FunctionalInterface
    public interface AdvancementEarned {
        void onAdvancementEarned(ServerPlayer player, AdvancementHolder advancement);
    }

    /**
     * A player reeled in a catch with a fishing rod, on the server: what the fishing loot table gave, before it's thrown
     * to them. Not fired for a hooked entity.
     *
     * @param items what they caught, to add to or change (a mutable list); each is thrown to them
     */
    @FunctionalInterface
    public interface ItemFished {
        void onItemFished(ServerPlayer player, FishingHook hook, List<ItemStack> items);
    }

    /**
     * How fast a player breaks a block, on both sides (check {@code player.level().isClientSide()}): the speed the game
     * divides by the block's hardness each tick. Interrupt with another speed to change it; each listener is given the
     * speed the ones before it chose. Change it the same way on both sides, or breaking looks wrong to the player.
     *
     * @param pos   where the block is, when the game says
     * @param speed the speed so far (the tool, enchantments, effects, being underwater or in the air)
     */
    @FunctionalInterface
    public interface BreakSpeed {
        EventResultHolder<Float> onBreakSpeed(Player player, BlockState state, @Nullable BlockPos pos, float speed);
    }

    /**
     * Whether a player's tool is right for a block, on both sides: without it the block breaks slower and drops nothing
     * (stone without a pickaxe). Interrupt with true or false to decide.
     *
     * @param pos where the block is, when the game says
     */
    @FunctionalInterface
    public interface HarvestCheck {
        EventResultHolder<Boolean> onHarvestCheck(Player player, BlockState state, @Nullable BlockPos pos, boolean canHarvest);
    }
}
