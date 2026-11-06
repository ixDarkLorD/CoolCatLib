package net.ixdarklord.coolcatcore.api.block;

import net.ixdarklord.coolcatcore.internal.block.BlockViewRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.Collection;

/**
 * Blocks that look like other blocks to some players, on the server: what the server sends them (the chunks, every
 * change to a block, block entities' data) has the other block in its place, so their client never knows the real one.
 * For example, ores that look like stone until a player may mine them.
 * <p>
 * Only what the player sees changes: the block is still the real one on the server, for breaking, drops and everything
 * else (see {@code PlayerEvents.BREAK_SPEED}, {@code HARVEST_CHECK} and {@code BlockEvents.DROPS} to make it act like
 * the other block too). Block entities of a block that looks like another aren't sent at all. Light isn't changed.
 * <p>
 * Views apply in the order they were registered, each to what the one before it made. When what a player should see
 * changes (they unlocked something), {@link #refresh(ServerPlayer)} sends them their chunks again.
 */
public final class BlockViews {
    private BlockViews() {}

    /** Adds a view. Call it once, while your mod constructs or the server starts; the id must be new. */
    public static void register(ResourceLocation id, BlockView view) {
        BlockViewRegistry.register(id, view);
    }

    /** Sends a player all the chunks they can see again, as they should look to them now. */
    public static void refresh(ServerPlayer player) {
        BlockViewRegistry.refresh(player, null);
    }

    /** Sends a player these chunks again (the ones they can see), as they should look to them now. */
    public static void refresh(ServerPlayer player, Collection<ChunkPos> chunks) {
        BlockViewRegistry.refresh(player, chunks);
    }

    /** Sends every player their chunks again: after a change that affects everyone (a config reloaded). */
    public static void refreshAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) BlockViewRegistry.refresh(player, null);
    }
}
