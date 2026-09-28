package net.ixdarklord.coolcatcanvas.internal.sky;

import net.ixdarklord.coolcatcore.api.network.Network;
import net.ixdarklord.coolcatcanvas.internal.client.sky.SkyboxPackets;

public final class SkyboxNetwork {
    private SkyboxNetwork() {}

    public static void init() {
        Network.registerClientbound(SkyboxPayload.TYPE, SkyboxPayload.STREAM_CODEC, (payload, context) -> SkyboxPackets.handle(payload));
    }
}
