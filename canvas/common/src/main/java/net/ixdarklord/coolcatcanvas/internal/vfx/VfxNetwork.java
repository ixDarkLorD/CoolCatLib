package net.ixdarklord.coolcatcanvas.internal.vfx;

import net.ixdarklord.coolcatcanvas.internal.client.vfx.VfxClientPackets;
import net.ixdarklord.coolcatcore.api.network.Network;

public final class VfxNetwork {
    private VfxNetwork() {}

    public static void init() {
        Network.registerClientbound(ShakePayload.TYPE, ShakePayload.STREAM_CODEC, (payload, context) -> VfxClientPackets.shake(payload));
    }
}
