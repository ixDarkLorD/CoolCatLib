package net.ixdarklord.coolcatcore.internal.event;

import net.ixdarklord.coolcatcore.api.event.v2.common.BlockEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * BlockEvents.DROPS where the loader has no event of its own (Fabric's and Forge's BlockMixin): a block's drops are held
 * back, and so is the experience it pops while they are, until both have been told about; then they drop. While a
 * player breaks a block ({@link #breaking}), they're held until the breaking is done, as a loader may pop its experience
 * after the drops (Forge does).
 */
public final class BlockDrops {
    private static final ThreadLocal<Deque<Drop>> DROPS = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<BlockPos> BREAKING = new ThreadLocal<>();

    private BlockDrops() {}

    /**
     * Drops a broken block's items the way the game does, but told about first: they're held while {@code afterBreak}
     * runs (the block's own after-breaking, which pops its experience), then dropped. Only while anyone listens.
     */
    public static void handle(ServerLevel level, BlockPos pos, BlockState state, @Nullable Entity breaker, ItemStack tool, List<ItemStack> drops, Runnable afterBreak) {
        hold(level, pos, state, breaker, tool, drops);
        afterBreak.run();
        release(level, pos);
    }

    /** What the game drops now: nothing, when the drops are held back to be told about first. */
    public static List<ItemStack> hold(ServerLevel level, BlockPos pos, BlockState state, @Nullable Entity breaker, ItemStack tool, List<ItemStack> drops) {
        if (!BlockEvents.DROPS.hasListeners()) return drops;
        Deque<Drop> held = DROPS.get();
        if (held.size() > 64) held.clear(); // (only drops that threw are left over)
        held.push(new Drop(level, pos.immutable(), state, breaker, tool, new ArrayList<>(drops), new MutableInt()));
        return List.of();
    }

    /** Whether experience popped now is held back (it's added to the drop's). */
    public static boolean holdExperience(ServerLevel level, BlockPos pos, int amount) {
        Drop drop = DROPS.get().peek();
        if (drop == null || drop.level != level || !drop.pos.equals(pos)) return false;
        drop.experience.add(amount);
        return true;
    }

    /** The drops' own call is done: they drop now, unless a player is still breaking the block. */
    public static void release(ServerLevel level, BlockPos pos) {
        if (pos.equals(BREAKING.get())) return;
        drop(level, pos);
    }

    /** A player starts breaking a block; returns the block they were breaking before, to give back to {@link #broken}. */
    public static @Nullable BlockPos breaking(BlockPos pos) {
        BlockPos outer = BREAKING.get();
        BREAKING.set(pos.immutable());
        return outer;
    }

    public static void broken(ServerLevel level, BlockPos pos, @Nullable BlockPos outer) {
        BREAKING.set(outer);
        drop(level, pos);
    }

    private static void drop(ServerLevel level, BlockPos pos) {
        Deque<Drop> held = DROPS.get();
        Drop drop = held.peek();
        if (drop == null || drop.level != level || !drop.pos.equals(pos)) return;
        held.pop();
        BlockEvents.DROPS.invoker().onDrops(level, drop.pos, drop.state, drop.breaker, drop.tool, drop.items, drop.experience);
        for (ItemStack stack : drop.items) Block.popResource(level, drop.pos, stack);
        if (drop.experience.intValue() > 0 && level.getGameRules().get(GameRules.BLOCK_DROPS)) {
            ExperienceOrb.award(level, Vec3.atCenterOf(drop.pos), drop.experience.intValue());
        }
    }

    private record Drop(ServerLevel level, BlockPos pos, BlockState state, @Nullable Entity breaker, ItemStack tool, List<ItemStack> items, MutableInt experience) {}
}
