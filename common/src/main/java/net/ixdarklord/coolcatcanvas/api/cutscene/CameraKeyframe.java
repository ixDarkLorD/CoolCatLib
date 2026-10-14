package net.ixdarklord.coolcatcanvas.api.cutscene;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.coolcatcanvas.api.utils.Easing;
import net.ixdarklord.coolcatcanvas.api.utils.Easings;
import net.minecraft.world.phys.Vec3;

/**
 * One pose a {@link Cutscene}'s camera passes through, and how it gets there from the keyframe before.
 *
 * @param time          when the camera is here, in ticks from the cutscene's start (fractions allowed)
 * @param position      where, in the cutscene's own space (see {@link CutsceneAnchor})
 * @param yaw           Minecraft's degrees (0 looks south, 90 west). The camera always turns the short way round
 *                      between two keyframes; a full spin takes keyframes less than half a turn apart
 * @param pitch         degrees down from the horizon
 * @param roll          degrees of tilt around the line of sight
 * @param fov           the vertical field of view in degrees, or 0 for the player's own setting
 * @param easing        how the move into this keyframe is paced; its name must be known to {@link Easings} for the
 *                      cutscene to be saved or sent
 * @param interpolation the shape of the move into this keyframe
 */
public record CameraKeyframe(float time, Vec3 position, float yaw, float pitch, float roll, float fov, Easing easing, Interpolation interpolation) {
    public static final Codec<CameraKeyframe> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.fieldOf("time").forGetter(CameraKeyframe::time),
            Vec3.CODEC.fieldOf("pos").forGetter(CameraKeyframe::position),
            Codec.FLOAT.optionalFieldOf("yaw", 0.0F).forGetter(CameraKeyframe::yaw),
            Codec.FLOAT.optionalFieldOf("pitch", 0.0F).forGetter(CameraKeyframe::pitch),
            Codec.FLOAT.optionalFieldOf("roll", 0.0F).forGetter(CameraKeyframe::roll),
            Codec.FLOAT.optionalFieldOf("fov", 0.0F).forGetter(CameraKeyframe::fov),
            Easings.CODEC.optionalFieldOf("easing", Easing.LINEAR).forGetter(CameraKeyframe::easing),
            Interpolation.CODEC.optionalFieldOf("interpolation", Interpolation.SMOOTH).forGetter(CameraKeyframe::interpolation)
    ).apply(instance, CameraKeyframe::new));

    public CameraKeyframe {
        if (!(time >= 0.0F)) throw new IllegalArgumentException("A keyframe's time can't be negative: " + time);
        if (!(fov >= 0.0F) || fov >= 180.0F) throw new IllegalArgumentException("Field of view must be 0 (the player's) or under 180: " + fov);
    }

    /** A keyframe reached smoothly at an even pace, with no roll and the player's own field of view. */
    public CameraKeyframe(float time, Vec3 position, float yaw, float pitch) {
        this(time, position, yaw, pitch, 0.0F, 0.0F, Easing.LINEAR, Interpolation.SMOOTH);
    }

    public CameraKeyframe withTime(float time) {
        return new CameraKeyframe(time, this.position, this.yaw, this.pitch, this.roll, this.fov, this.easing, this.interpolation);
    }

    public CameraKeyframe withRoll(float roll) {
        return new CameraKeyframe(this.time, this.position, this.yaw, this.pitch, roll, this.fov, this.easing, this.interpolation);
    }

    public CameraKeyframe withFov(float fov) {
        return new CameraKeyframe(this.time, this.position, this.yaw, this.pitch, this.roll, fov, this.easing, this.interpolation);
    }

    public CameraKeyframe withEasing(Easing easing) {
        return new CameraKeyframe(this.time, this.position, this.yaw, this.pitch, this.roll, this.fov, easing, this.interpolation);
    }

    public CameraKeyframe withInterpolation(Interpolation interpolation) {
        return new CameraKeyframe(this.time, this.position, this.yaw, this.pitch, this.roll, this.fov, this.easing, interpolation);
    }
}
