package net.ixdarklord.coolcatcanvas.api.cutscene;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Where a camera is and how it looks: one instant of a {@link Cutscene}.
 *
 * @param yaw   the turn around the vertical, in Minecraft's degrees (0 looks south, 90 west)
 * @param pitch degrees down from the horizon (negative looks up)
 * @param roll  degrees of tilt around the line of sight: a dutch angle
 * @param fov   the vertical field of view, in degrees
 */
public record CameraPose(Vec3 position, float yaw, float pitch, float roll, float fov) {
    /** The unit vector this pose looks along. */
    public Vec3 forward() {
        return Vec3.directionFromRotation(this.pitch, this.yaw);
    }

    /** This pose moved from a cutscene's own space into the world: turned around the vertical, then shifted. */
    public CameraPose transformed(Vec3 origin, float originYaw) {
        if (originYaw == 0.0F) return origin.equals(Vec3.ZERO) ? this : new CameraPose(this.position.add(origin), this.yaw, this.pitch, this.roll, this.fov);
        return new CameraPose(rotate(this.position, originYaw).add(origin), this.yaw + originYaw, this.pitch, this.roll, this.fov);
    }

    /** Part of the way from this pose to another, turning the short way round. */
    public CameraPose lerp(CameraPose to, float t) {
        if (t <= 0.0F) return this;
        if (t >= 1.0F) return to;
        return new CameraPose(this.position.lerp(to.position, t), this.yaw + Mth.wrapDegrees(to.yaw - this.yaw) * t,
                Mth.lerp(t, this.pitch, to.pitch), this.roll + Mth.wrapDegrees(to.roll - this.roll) * t, Mth.lerp(t, this.fov, to.fov));
    }

    /** This pose turned to look at a point, by {@code weight}: 0 leaves it alone, 1 looks straight at it. */
    public CameraPose lookingAt(Vec3 target, float weight) {
        if (weight <= 0.0F) return this;
        Vec3 delta = target.subtract(this.position);
        double flat = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (flat < 1.0E-4 && Math.abs(delta.y) < 1.0E-4) return this;
        float yaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(delta.y, flat) * Mth.RAD_TO_DEG);
        return new CameraPose(this.position, this.yaw + Mth.wrapDegrees(yaw - this.yaw) * weight, Mth.lerp(weight, this.pitch, pitch), this.roll, this.fov);
    }

    /** A point turned around the vertical by a yaw, the way a player facing that yaw would see their own space. */
    public static Vec3 rotate(Vec3 point, float yaw) {
        float radians = yaw * Mth.DEG_TO_RAD;
        float sin = Mth.sin(radians);
        float cos = Mth.cos(radians);
        return new Vec3(point.x * cos - point.z * sin, point.y, point.x * sin + point.z * cos);
    }
}
