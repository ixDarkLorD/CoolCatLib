package net.ixdarklord.coolcatcanvas.api.vfx;

import net.ixdarklord.coolcatcanvas.internal.vfx.ShakePayload;
import net.ixdarklord.coolcatcore.api.network.Network;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Camera shake from the server (the client side is {@code CameraShake}). A shake is one small packet; players whose
 * client lacks CoolCatLib are skipped, and each player's own accessibility setting still scales it.
 * <pre>{@code
 * ShakeControl.shake(level, blastPos, 24.0F, 4.0F, 20);     // everyone within 24 blocks, strongest at the blast
 * ShakeControl.shake(player, 1.5F, 12);                     // this player only
 * }</pre>
 */
public final class ShakeControl {
    private ShakeControl() {}

    /**
     * Shakes the camera of everyone within {@code radius} of a place: full strength there, nothing at the edge.
     *
     * @param degrees how far the camera turns at the origin; it dies away over {@code ticks}
     */
    public static void shake(ServerLevel level, Vec3 origin, float radius, float degrees, int ticks) {
        ShakePayload payload = new ShakePayload(origin, radius, degrees, ticks);
        double rangeSqr = (double) radius * radius;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(origin) <= rangeSqr && Network.canPlayerReceive(player, ShakePayload.TYPE)) Network.sendToPlayer(player, payload);
        }
    }

    /** Shakes one player's camera, wherever they are. */
    public static void shake(ServerPlayer player, float degrees, int ticks) {
        if (Network.canPlayerReceive(player, ShakePayload.TYPE)) Network.sendToPlayer(player, new ShakePayload(Vec3.ZERO, 0.0F, degrees, ticks));
    }
}
