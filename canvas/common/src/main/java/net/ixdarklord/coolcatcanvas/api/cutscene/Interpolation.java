package net.ixdarklord.coolcatcanvas.api.cutscene;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** How the camera gets from the keyframe before to a {@link CameraKeyframe}. */
public enum Interpolation implements StringRepresentable {
    /** A straight line at an even turn: reads as mechanical, good for pans and dolly shots. */
    LINEAR("linear"),
    /** A curve through the keyframes around it, with no corner at either end: the default. */
    SMOOTH("smooth"),
    /** No movement: the camera stays on the keyframe before, then cuts here. */
    CUT("cut");

    public static final Codec<Interpolation> CODEC = StringRepresentable.fromEnum(Interpolation::values);

    private final String name;

    Interpolation(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
