package net.ixdarklord.coolcatcanvas.internal.vfx;

import net.ixdarklord.coolcatcanvas.internal.client.vfx.VfxClientPackets;
import net.ixdarklord.coolcatcanvas.internal.client.vfx.VfxManager;
import net.ixdarklord.coolcatcore.api.network.Network;

public final class VfxNetwork {
    private VfxNetwork() {}

    public static void init() {
        Network.registerClientbound(VfxPayload.TYPE, VfxPayload.STREAM_CODEC, (payload, context) -> VfxManager.playEffect(payload.effect(), payload.params()));
        Network.registerClientbound(ShakePayload.TYPE, ShakePayload.STREAM_CODEC, (payload, context) -> VfxClientPackets.shake(payload));
        Network.registerClientbound(RiftPayload.TYPE, RiftPayload.STREAM_CODEC, (payload, context) -> VfxClientPackets.rift(payload));
    }
}
