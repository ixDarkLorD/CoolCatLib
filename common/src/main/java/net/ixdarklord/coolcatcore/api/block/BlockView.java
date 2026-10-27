package net.ixdarklord.coolcatcore.api.block;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * How some blocks look to some players: see {@link BlockViews}. Players are grouped by their {@link #key}, so what
 * blocks look like is worked out once for each group, not once for each player.
 * <pre>{@code
 * BlockViews.register(MyMod.id("hidden_ores"), new BlockView() {
 *     public Object key(ServerPlayer player) {
 *         int tier = Tiers.of(player);
 *         return tier >= MAX_TIER ? null : tier; // players who see everything see blocks as they are
 *     }
 *
 *     public BlockState view(Object key, BlockState state) {
 *         return Tiers.isLockedAt(state, (int) key) ? Tiers.disguiseOf(state) : state;
 *     }
 * });
 * }</pre>
 */
public interface BlockView {
    /**
     * What decides how blocks look to this player, or null when this view leaves every block as it is for them (the
     * cheapest: nothing is worked out for them). Players with equal keys (by {@code equals}) see the same.
     */
    @Nullable Object key(ServerPlayer player);

    /**
     * What a block looks like to the players with this key: another state, or the block itself to leave it. Given the
     * same key and state, answer the same each time. Don't make a block air, or the client miscounts its blocks.
     */
    BlockState view(Object key, BlockState state);
}
