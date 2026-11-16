package net.ixdarklord.coolcatcanvas.internal.client.armature;

import net.ixdarklord.coolcatcanvas.internal.client.armature.vanilla.VanillaArmatures;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientPlayerEvents;

/** Armatures on the client: trails, and forgetting animators on leaving a world. */
public final class ArmatureClient {
    private ArmatureClient() {
    }

    public static void init() {
        ClientPlayerEvents.LEAVE.register(player -> ArmatureInstances.clear());
        ArmatureVfx.init();
        VanillaArmatures.init();
    }
}
