package net.ixdarklord.coolcatcanvas.api.client.camera;

import net.ixdarklord.coolcatcanvas.api.client.vfx.Curve;
import net.ixdarklord.coolcatcanvas.internal.client.camera.CameraShakeManager;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Shakes the camera: an explosion nearby, a giant's footstep. Shakes add up, fade over their
 * own time, and follow the game's "Distortion Effects" accessibility slider, so a player who turned that down gets
 * less of them, and none at zero.
 * <pre>{@code
 * CameraShake.shake(1.5F, 12);                                   // a short jolt, wherever the player is
 * CameraShake.shake(blastPos, 24.0F, 4.0F, 20);                  // strong at the blast, nothing 24 blocks away
 * CameraShake.add(new CameraShake.Shake(Curve.of(0.0F, 2.0F, 0.0F), 60, 0.8F, null, 0.0F));   // a slow swell
 * }</pre>
 * From the server, {@code ShakeControl} does the same for players in range.
 */
public final class CameraShake {
    private CameraShake() {}

    /**
     * One shake.
     *
     * @param intensity how far the camera turns, in degrees, over the shake's life (its start, middle and end)
     * @param ticks     how long it lasts
     * @param frequency how fast it rattles; 1 is a sharp impact, lower is a rumble
     * @param origin    where it comes from, or null for one felt the same everywhere
     * @param radius    with an origin: the distance at which nothing is felt any more
     */
    public record Shake(Curve intensity, int ticks, float frequency, @Nullable Vec3 origin, float radius) {
        public Shake {
            if (ticks < 1) throw new IllegalArgumentException("A shake lasts at least a tick: " + ticks);
            if (frequency <= 0.0F) throw new IllegalArgumentException("Frequency must be positive: " + frequency);
        }
    }

    /** A jolt that starts at full strength and dies away, felt wherever the player is. */
    public static void shake(float degrees, int ticks) {
        add(new Shake(Curve.of(degrees, 0.0F), ticks, 1.0F, null, 0.0F));
    }

    /** A jolt from a place: full strength there, fading to nothing {@code radius} blocks away. */
    public static void shake(Vec3 origin, float radius, float degrees, int ticks) {
        add(new Shake(Curve.of(degrees, 0.0F), ticks, 1.0F, origin, radius));
    }

    public static void add(Shake shake) {
        CameraShakeManager.add(shake);
    }

    /** Stops every shake at once, e.g. for a cutscene. */
    public static void clear() {
        CameraShakeManager.clear();
    }

    /**
     * Scales every shake, on top of the player's own accessibility setting: for a mod's own "screen shake" option.
     * 1 by default.
     */
    public static void setScale(float scale) {
        CameraShakeManager.setScale(scale);
    }

    public static float scale() {
        return CameraShakeManager.scale();
    }
}
