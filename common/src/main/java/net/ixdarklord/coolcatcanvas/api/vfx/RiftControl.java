package net.ixdarklord.coolcatcanvas.api.vfx;

import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.ixdarklord.coolcatcanvas.internal.vfx.RiftPayload;
import net.ixdarklord.coolcatcore.api.network.Network;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Rifts from the server (the client side is {@code WorldRifts}). The server names a rift the clients registered;
 * each client runs its three acts, sounds included, on its own.
 * <pre>{@code
 * RiftControl.open(level, RiftControl.DEFAULT, pos, 100);            // closes by itself after five seconds open
 *
 * UUID rift = RiftControl.open(level, MyMod.id("ember_rift"), pos);  // stays...
 * RiftControl.close(level, rift);                                    // ...until closed
 * }</pre>
 * A rift is not an entity and is not saved: a player who comes into range, or logs in, after it opened does not see
 * it. For one that must last, open it again for them, or give it to something that already syncs.
 */
public final class RiftControl {
    /** The rift every client has: a violet tear with vanilla's portal sounds. */
    public static final ResourceLocation DEFAULT = CoolCatCanvas.rl("rift");
    public static final double DEFAULT_RANGE = 96.0;

    private RiftControl() {}

    /** Opens a rift that stays until {@link #close}. Returns the id to close it with. */
    public static UUID open(ServerLevel level, ResourceLocation spec, Vec3 position) {
        return open(level, spec, position, -1);
    }

    /**
     * Opens a rift for everyone within {@link #DEFAULT_RANGE} of it.
     *
     * @param openTicks how long it stays fully open before closing by itself; negative for until {@link #close}
     */
    public static UUID open(ServerLevel level, ResourceLocation spec, Vec3 position, int openTicks) {
        UUID id = UUID.randomUUID();
        RiftPayload payload = new RiftPayload(true, id, spec, position, Math.max(-1, openTicks));
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(position) <= DEFAULT_RANGE * DEFAULT_RANGE && Network.canPlayerReceive(player, RiftPayload.TYPE)) {
                Network.sendToPlayer(player, payload);
            }
        }
        return id;
    }

    /** Closes a rift for everyone in the level who has it. */
    public static void close(ServerLevel level, UUID rift) {
        RiftPayload payload = new RiftPayload(false, rift, DEFAULT, Vec3.ZERO, -1);
        for (ServerPlayer player : level.players()) {
            if (Network.canPlayerReceive(player, RiftPayload.TYPE)) Network.sendToPlayer(player, payload);
        }
    }
}
