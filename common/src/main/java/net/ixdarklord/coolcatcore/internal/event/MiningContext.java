package net.ixdarklord.coolcatcore.internal.event;

import net.ixdarklord.coolcatcore.api.event.v2.common.PlayerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * PlayerEvents.BREAK_SPEED and HARVEST_CHECK where the loader has no events of its own (Fabric's PlayerMixin): the
 * player's own methods don't take the block's position, so the callers that know it say it around their call.
 */
public final class MiningContext {
    private static final ThreadLocal<BlockPos> POS = new ThreadLocal<>();

    private MiningContext() {}

    /** Says where the block is until {@link #exit}; returns the position said before, to give back to it. */
    public static @Nullable BlockPos enter(BlockPos pos) {
        BlockPos outer = POS.get();
        POS.set(pos);
        return outer;
    }

    public static void exit(@Nullable BlockPos outer) {
        POS.set(outer);
    }

    public static float breakSpeed(Player player, BlockState state, float speed) {
        if (!PlayerEvents.BREAK_SPEED.hasListeners()) return speed;
        return PlayerEvents.BREAK_SPEED.invoker().onBreakSpeed(player, state, POS.get(), speed).getInterrupt().orElse(speed);
    }

    public static boolean canHarvest(Player player, BlockState state, boolean canHarvest) {
        if (!PlayerEvents.HARVEST_CHECK.hasListeners()) return canHarvest;
        return PlayerEvents.HARVEST_CHECK.invoker().onHarvestCheck(player, state, POS.get(), canHarvest).getInterrupt().orElse(canHarvest);
    }
}
