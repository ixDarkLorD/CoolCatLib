package net.ixdarklord.coolcatcore.api.network;

import net.ixdarklord.coolcatcore.api.platform.Env;
import net.minecraft.world.entity.player.Player;

/**
 * Where a received payload came from.
 */
public interface PacketContext {
    /**
     * The player it's about: the sender on the server, the local player on the client.
     */
    Player getPlayer();

    /**
     * Runs a task on the receiving side's main thread.
     */
    void queue(Runnable task);

    /**
     * The side that received the payload.
     */
    Env getEnvironment();
}
