package net.ixdarklord.coolcatcore.api.event.v2.common;

import net.ixdarklord.coolcatcore.api.event.v2.core.EventInvoker;
import net.ixdarklord.coolcatcore.api.event.v2.core.EventResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A player using or attacking blocks, items and entities, before the game acts on it. These fire on both sides, once
 * for each hand tried; check {@code player.level().isClientSide()}. To stop a use for good, answer the same on both
 * sides (the client otherwise still predicts it).
 */
public final class InteractionEvents {
    public static final EventInvoker<UseBlock> USE_BLOCK = EventInvoker.create(UseBlock.class, listeners -> (player, hand, hit) -> {
        for (UseBlock listener : listeners) {
            InteractionResult result = listener.onUseBlock(player, hand, hit);
            if (result != InteractionResult.PASS) return result;
        }
        return InteractionResult.PASS;
    });
    public static final EventInvoker<UseItem> USE_ITEM = EventInvoker.create(UseItem.class, listeners -> (player, hand) -> {
        for (UseItem listener : listeners) {
            InteractionResult result = listener.onUseItem(player, hand);
            if (result != InteractionResult.PASS) return result;
        }
        return InteractionResult.PASS;
    });
    public static final EventInvoker<UseEntity> USE_ENTITY = EventInvoker.create(UseEntity.class, listeners -> (player, hand, target) -> {
        for (UseEntity listener : listeners) {
            InteractionResult result = listener.onUseEntity(player, hand, target);
            if (result != InteractionResult.PASS) return result;
        }
        return InteractionResult.PASS;
    });
    public static final EventInvoker<AttackBlock> ATTACK_BLOCK = EventInvoker.create(AttackBlock.class, listeners -> (player, hand, pos, face) -> {
        for (AttackBlock listener : listeners) {
            EventResult result = listener.onAttackBlock(player, hand, pos, face);
            if (result.isInterrupt()) return result;
        }
        return EventResult.PASS;
    });
    public static final EventInvoker<AttackEntity> ATTACK_ENTITY = EventInvoker.create(AttackEntity.class, listeners -> (player, target) -> {
        for (AttackEntity listener : listeners) {
            EventResult result = listener.onAttackEntity(player, target);
            if (result.isInterrupt()) return result;
        }
        return EventResult.PASS;
    });

    private InteractionEvents() {}

    /**
     * A player is about to use (right-click) a block, with whatever is in the hand. {@code PASS} lets the game go on;
     * anything else is the use's result and stops it there (neither the block nor the item is used): {@code SUCCESS}
     * swings the arm, {@code CONSUME} and {@code FAIL} don't.
     */
    @FunctionalInterface
    public interface UseBlock {
        InteractionResult onUseBlock(Player player, InteractionHand hand, BlockHitResult hit);
    }

    /** A player is about to use (right-click) the item in a hand, aimed at no block or entity. Answered as {@link UseBlock}. */
    @FunctionalInterface
    public interface UseItem {
        InteractionResult onUseItem(Player player, InteractionHand hand);
    }

    /** A player is about to use (right-click) an entity. Answered as {@link UseBlock}. */
    @FunctionalInterface
    public interface UseEntity {
        InteractionResult onUseEntity(Player player, InteractionHand hand, Entity target);
    }

    /** A player starts hitting a block (the first click, not each tick of breaking it); interrupting stops it. */
    @FunctionalInterface
    public interface AttackBlock {
        EventResult onAttackBlock(Player player, InteractionHand hand, BlockPos pos, Direction face);
    }

    /** A player is about to attack an entity; interrupting stops the attack. */
    @FunctionalInterface
    public interface AttackEntity {
        EventResult onAttackEntity(Player player, Entity target);
    }
}
