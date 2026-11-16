package net.ixdarklord.coolcatcanvas.internal.effect;

import net.ixdarklord.coolcatcanvas.internal.client.effect.ScreenEffectPackets;
import net.ixdarklord.coolcatcore.api.network.Network;

public final class ScreenEffectNetwork {
    private ScreenEffectNetwork() {}

    public static void init() {
        Network.registerClientbound(ScreenEffectPayload.TYPE, ScreenEffectPayload.STREAM_CODEC, (payload, context) -> ScreenEffectPackets.handle(payload));
    }
}
