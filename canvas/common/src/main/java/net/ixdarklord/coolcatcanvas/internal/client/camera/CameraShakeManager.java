package net.ixdarklord.coolcatcanvas.internal.client.camera;

import net.ixdarklord.coolcatcanvas.api.client.camera.CameraShake;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

// Camera shake: the running shakes, and the turn they add up to for a frame. CameraMixin applies it once the camera
// has been aligned with its entity. The motion is a few sines of different speeds, so it is smooth between frames and
// never jumps, yet does not read as a wobble.
public final class CameraShakeManager {
    /** No shake turns the camera further than this, however many pile up. */
    private static final float MAX_DEGREES = 12.0F;
    private static final List<Running> RUNNING = new ArrayList<>();
    private static float scale = 1.0F;
    private static float yaw;
    private static float pitch;

    private CameraShakeManager() {}

    public static void add(CameraShake.Shake shake) {
        if (Minecraft.getInstance().level != null) RUNNING.add(new Running(shake, RUNNING.size() * 1.7F));
    }

    public static void clear() {
        RUNNING.clear();
    }

    public static void setScale(float value) {
        scale = Math.max(0.0F, value);
    }

    public static float scale() {
        return scale;
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.level == null) {
            RUNNING.clear();
            return;
        }
        if (!minecraft.isPaused()) RUNNING.removeIf(running -> ++running.age >= running.shake.ticks());
    }

    /** Works out this frame's turn. Called once a frame, before the camera is set up. */
    public static void update(Vec3 cameraPosition, float partialTick) {
        yaw = 0.0F;
        pitch = 0.0F;
        if (RUNNING.isEmpty()) return;
        float strength = scale * Minecraft.getInstance().options.screenEffectScale().get().floatValue();
        if (strength <= 0.0F) return;
        for (Running running : RUNNING) {
            CameraShake.Shake shake = running.shake;
            float time = running.age + partialTick;
            float degrees = shake.intensity().at(Math.min(1.0F, time / shake.ticks())) * strength;
            if (shake.origin() != null) {
                float distance = (float) shake.origin().distanceTo(cameraPosition);
                if (shake.radius() <= 0.0F || distance >= shake.radius()) continue;
                float near = 1.0F - distance / shake.radius();
                degrees *= near * near;
            }
            if (degrees <= 0.0F) continue;
            float t = time * shake.frequency() + running.phase;
            yaw += degrees * (Mth.sin(t * 2.9F) + 0.5F * Mth.sin(t * 6.7F + 1.3F)) / 1.5F;
            pitch += degrees * (Mth.sin(t * 3.7F + 2.1F) + 0.5F * Mth.sin(t * 7.9F + 0.4F)) / 1.5F;
        }
        yaw = Mth.clamp(yaw, -MAX_DEGREES, MAX_DEGREES);
        pitch = Mth.clamp(pitch, -MAX_DEGREES, MAX_DEGREES);
    }

    public static float yaw() {
        return yaw;
    }

    public static float pitch() {
        return pitch;
    }

    public static boolean active() {
        return !RUNNING.isEmpty();
    }

    private static final class Running {
        final CameraShake.Shake shake;
        final float phase;
        int age;

        Running(CameraShake.Shake shake, float phase) {
            this.shake = shake;
            this.phase = phase;
        }
    }
}
