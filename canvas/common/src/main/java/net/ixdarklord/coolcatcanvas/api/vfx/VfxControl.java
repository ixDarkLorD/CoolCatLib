package net.ixdarklord.coolcatcanvas.api.vfx;

import net.ixdarklord.coolcatcanvas.internal.vfx.VfxPayload;
import net.ixdarklord.coolcatcore.api.network.Network;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Plays world effects on players' clients from the server, by the id they were registered under with
 * {@code VfxEffects.register}. Clients without that effect ignore it, and players whose client can't take the packet
 * (no Canvas, or a fake player) are skipped.
 * <pre>{@code
 * VfxControl.play(level, MyMod.id("impact"), VfxParams.at(hit, 0xFF7A2A));
 * VfxControl.play(level, MyMod.id("bolt"), VfxParams.between(from, to, 0x9AD8FF));
 * }</pre>
 * One call is one small packet to each player near enough to see it, in place of the dozens a burst of vanilla
 * particles takes.
 */
public final class VfxControl {
    /** How far from an effect, in blocks, players are told about it by default. */
    public static final double DEFAULT_RANGE = 64.0;

    private VfxControl() {}

    /** Plays an effect for everyone in the level within {@link #DEFAULT_RANGE} of it. */
    public static void play(ServerLevel level, Identifier effect, VfxParams params) {
        play(level, effect, params, DEFAULT_RANGE);
    }

    public static void play(ServerLevel level, Identifier effect, VfxParams params, double range) {
        VfxPayload payload = new VfxPayload(effect, params);
        double rangeSqr = range * range;
        for (ServerPlayer player : level.players()) {
            if ((player.distanceToSqr(params.position()) <= rangeSqr || player.distanceToSqr(params.target()) <= rangeSqr)
                    && Network.canPlayerReceive(player, VfxPayload.TYPE)) {
                Network.sendToPlayer(player, payload);
            }
        }
    }

    /** Plays an effect for one player only. */
    public static void play(ServerPlayer player, Identifier effect, VfxParams params) {
        if (Network.canPlayerReceive(player, VfxPayload.TYPE)) Network.sendToPlayer(player, new VfxPayload(effect, params));
    }
}
