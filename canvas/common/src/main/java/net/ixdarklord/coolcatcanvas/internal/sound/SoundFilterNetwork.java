package net.ixdarklord.coolcatcanvas.internal.sound;

import net.ixdarklord.coolcatcanvas.internal.client.sound.SoundFilterClientPackets;
import net.ixdarklord.coolcatcore.api.network.Network;

public final class SoundFilterNetwork {
    private SoundFilterNetwork() {}

    public static void init() {
        Network.registerClientbound(SoundFilterPayload.TYPE, SoundFilterPayload.STREAM_CODEC, (payload, context) -> SoundFilterClientPackets.handle(payload));
    }
}
