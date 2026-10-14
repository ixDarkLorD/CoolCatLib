package net.ixdarklord.coolcatcanvas.internal.client.vfx;

import net.ixdarklord.coolcatcanvas.api.client.camera.CameraShake;
import net.ixdarklord.coolcatcanvas.api.client.vfx.Curve;
import net.ixdarklord.coolcatcanvas.internal.vfx.ShakePayload;
import net.minecraft.util.Mth;

// What the client does with the server's shake packets.
public final class VfxClientPackets {
    private VfxClientPackets() {}

    public static void shake(ShakePayload payload) {
        // Nothing a server sends shakes a camera for longer than half a minute or harder than the manager allows.
        int ticks = Mth.clamp(payload.ticks(), 1, 600);
        float degrees = Mth.clamp(payload.degrees(), 0.0F, 12.0F);
        CameraShake.add(new CameraShake.Shake(Curve.of(degrees, 0.0F), ticks, 1.0F, payload.radius() > 0.0F ? payload.origin() : null, payload.radius()));
    }
}
