package net.ixdarklord.coolcatcanvas.internal.client.armature;

import net.ixdarklord.coolcatcanvas.internal.client.armature.render.ArmatureItemRenderer;
import net.ixdarklord.coolcatcanvas.internal.client.armature.vanilla.VanillaArmatures;
import net.ixdarklord.coolcatcanvas.internal.mixin.client.SpecialModelRenderersAccessor;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientPlayerEvents;

/** Armatures on the client: the armature item model type, trails, and forgetting animators on leaving a world. */
public final class ArmatureClient {
    private ArmatureClient() {
    }

    public static void init() {
        SpecialModelRenderersAccessor.coolcatcanvas$idMapper().put(ArmatureItemRenderer.TYPE, ArmatureItemRenderer.Unbaked.MAP_CODEC);
        ClientPlayerEvents.LEAVE.register(player -> ArmatureInstances.clear());
        ArmatureVfx.init();
        VanillaArmatures.init();
    }
}
