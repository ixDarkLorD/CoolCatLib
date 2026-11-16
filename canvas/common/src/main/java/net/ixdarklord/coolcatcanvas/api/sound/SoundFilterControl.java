package net.ixdarklord.coolcatcanvas.api.sound;

import net.ixdarklord.coolcatcanvas.internal.sound.SoundFilterPayload;
import net.ixdarklord.coolcatcore.api.network.Network;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Named sound filters from the server (the client side, and what each part does, is {@code SoundFilters} and
 * {@code SoundFilter}): one small packet to set or clear a filter on a player, which then fades on its own. Players
 * whose client lacks CoolCatLib are skipped.
 * <pre>{@code
 * SoundFilterControl.set(player, MyMod.id("dazed"), 0.7F, 0.15F, 0.2F, 0.0F, 20);   // muffled and rough, over a second
 * SoundFilterControl.clear(player, MyMod.id("dazed"), 60);                          // and back over three
 * }</pre>
 */
public final class SoundFilterControl {
    private SoundFilterControl() {}

    /**
     * Fades a named filter in on a player's client over {@code fadeTicks}, replacing any of that name.
     *
     * @param gain       how loud sounds stay, 0 to 1
     * @param gainHF     how much of their high end they keep, 0 to 1
     * @param distortion how much of them goes through the distortion, 0 to 1
     * @param reverb     how much of them goes into the reverb, 0 to 1
     */
    public static void set(ServerPlayer player, Identifier id, float gain, float gainHF, float distortion, float reverb, int fadeTicks) {
        send(player, new SoundFilterPayload(id, false, gain, gainHF, distortion, reverb, fadeTicks));
    }

    /** Fades a named filter out on a player's client over {@code fadeTicks}, then removes it. */
    public static void clear(ServerPlayer player, Identifier id, int fadeTicks) {
        send(player, new SoundFilterPayload(id, true, 1.0F, 1.0F, 0.0F, 0.0F, fadeTicks));
    }

    private static void send(ServerPlayer player, SoundFilterPayload payload) {
        if (Network.canPlayerReceive(player, SoundFilterPayload.TYPE)) Network.sendToPlayer(player, payload);
    }
}
