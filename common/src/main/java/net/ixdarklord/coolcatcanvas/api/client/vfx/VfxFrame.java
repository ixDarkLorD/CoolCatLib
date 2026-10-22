package net.ixdarklord.coolcatcanvas.api.client.vfx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/** The frame a {@link VfxRenderer} is drawing into. */
public interface VfxFrame {
    ClientLevel level();

    /** How far the frame is between the last tick and the next, 0 to 1. */
    float partialTick();

    /** Seconds since the client started, for animation that shouldn't step with ticks. */
    float time();

    Vec3 cameraPosition();

    /** The drawer for a material. Shapes of equal materials are batched, whoever draws them. */
    VfxDraw draw(VfxMaterial material);
}
