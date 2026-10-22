package net.ixdarklord.coolcatcanvas.internal.armature;

import net.ixdarklord.coolcatcanvas.internal.client.armature.ArmatureClientPackets;
import net.ixdarklord.coolcatcore.api.network.Network;

public final class ArmatureNetwork {
    private ArmatureNetwork() {}

    public static void init() {
        Network.registerClientbound(ArmatureCommandPayload.TYPE, ArmatureCommandPayload.STREAM_CODEC, (payload, context) -> ArmatureClientPackets.handle(payload));
    }
}
