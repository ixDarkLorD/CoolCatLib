package net.ixdarklord.coolcatcanvas.api.client.armature;

import net.minecraft.util.Mth;

import java.util.Arrays;

/**
 * How far each joint is moved from its rest this frame: what the animation layers made of it, open to
 * {@linkplain JointModifier modifiers} before it is drawn. Values are in the units of Blockbench's animation panel:
 * degrees and pixels, with Bedrock's axes, so {@code rotate("head", 20, 0, 0)} does what a rotation keyframe of
 * {@code [20, 0, 0]} would. Joints are named or given by index; a name the armature doesn't have is ignored.
 */
public final class ArmaturePose {
    final BakedArmature armature;
    // Kept in the model's own axes (radians, pixels), ready for the joint matrices.
    final float[] rotation;
    final float[] position;
    final float[] scale;
    final boolean[] hidden;
    // Each joint's bend: how far (radians), which way round its length (radians, 0 forward), and a spare.
    final float[] bend;
    // How much of each joint's rotation, position, scale and bend the override layers took over, 0 to 1.
    final float[] cover;

    ArmaturePose(BakedArmature armature) {
        int n = armature.jointCount();
        this.armature = armature;
        this.rotation = new float[n * 3];
        this.position = new float[n * 3];
        this.scale = new float[n * 3];
        this.hidden = new boolean[n];
        this.bend = new float[n * 3];
        this.cover = new float[n * 4];
        this.reset();
    }

    void reset() {
        Arrays.fill(this.rotation, 0.0F);
        Arrays.fill(this.position, 0.0F);
        Arrays.fill(this.scale, 1.0F);
        Arrays.fill(this.hidden, false);
        Arrays.fill(this.bend, 0.0F);
        Arrays.fill(this.cover, 0.0F);
    }

    /** How many floats {@link #read} writes. */
    public static final int VALUES = 17;

    /**
     * One joint's values in the model's own axes, for code that applies a pose elsewhere (CoolCatLib's vanilla models
     * do): {@code out[0..2]} rotation in radians, {@code [3..5]} position in pixels, {@code [6..8]} scale,
     * {@code [9..10]} bend amount and direction in radians, {@code [12..15]} how much override layers took over
     * rotation, position, scale and bend (0 to 1), {@code [16]} 1 if hidden. Blockbench's axes: Y up, facing north.
     */
    public void read(int joint, float[] out) {
        System.arraycopy(this.rotation, joint * 3, out, 0, 3);
        System.arraycopy(this.position, joint * 3, out, 3, 3);
        System.arraycopy(this.scale, joint * 3, out, 6, 3);
        System.arraycopy(this.bend, joint * 3, out, 9, 3);
        System.arraycopy(this.cover, joint * 4, out, 12, 4);
        out[16] = this.hidden[joint] ? 1.0F : 0.0F;
    }

    /**
     * Bends a joint along its length (a limb at its middle: an elbow, a knee), in degrees, on top of what the clips do.
     *
     * @param direction which way round the limb it bends, in degrees: 0 forward, 90 to its left, 180 back
     */
    public ArmaturePose bend(String joint, float degrees, float direction) {
        int j = this.armature.jointIndex(joint);
        if (j < 0) return this;
        this.bend[j * 3] += degrees * Mth.DEG_TO_RAD;
        this.bend[j * 3 + 1] = direction * Mth.DEG_TO_RAD;
        return this;
    }

    public BakedArmature armature() {
        return this.armature;
    }

    /** Turns a joint further, in degrees. */
    public ArmaturePose rotate(String joint, float x, float y, float z) {
        return this.rotate(this.armature.jointIndex(joint), x, y, z);
    }

    public ArmaturePose rotate(int joint, float x, float y, float z) {
        if (joint < 0) return this;
        this.rotation[joint * 3] -= x * Mth.DEG_TO_RAD;
        this.rotation[joint * 3 + 1] -= y * Mth.DEG_TO_RAD;
        this.rotation[joint * 3 + 2] += z * Mth.DEG_TO_RAD;
        return this;
    }

    /** Replaces whatever turn the animation gave a joint, in degrees. */
    public ArmaturePose setRotation(String joint, float x, float y, float z) {
        int j = this.armature.jointIndex(joint);
        if (j < 0) return this;
        this.rotation[j * 3] = 0.0F;
        this.rotation[j * 3 + 1] = 0.0F;
        this.rotation[j * 3 + 2] = 0.0F;
        return this.rotate(j, x, y, z);
    }

    /** A joint's turn from rest, in degrees, as {@code [x, y, z]}. */
    public float[] rotation(String joint) {
        int j = this.armature.jointIndex(joint);
        if (j < 0) return new float[3];
        return new float[]{-this.rotation[j * 3] * Mth.RAD_TO_DEG, -this.rotation[j * 3 + 1] * Mth.RAD_TO_DEG, this.rotation[j * 3 + 2] * Mth.RAD_TO_DEG};
    }

    /** Moves a joint (and everything on it), in pixels. */
    public ArmaturePose translate(String joint, float x, float y, float z) {
        return this.translate(this.armature.jointIndex(joint), x, y, z);
    }

    public ArmaturePose translate(int joint, float x, float y, float z) {
        if (joint < 0) return this;
        this.position[joint * 3] -= x;
        this.position[joint * 3 + 1] += y;
        this.position[joint * 3 + 2] += z;
        return this;
    }

    /** Scales a joint (and everything on it) further. */
    public ArmaturePose scale(String joint, float x, float y, float z) {
        return this.scale(this.armature.jointIndex(joint), x, y, z);
    }

    public ArmaturePose scale(int joint, float x, float y, float z) {
        if (joint < 0) return this;
        this.scale[joint * 3] *= x;
        this.scale[joint * 3 + 1] *= y;
        this.scale[joint * 3 + 2] *= z;
        return this;
    }

    public ArmaturePose scale(String joint, float scale) {
        return this.scale(joint, scale, scale, scale);
    }

    /** Hides a joint and everything on it for this frame only (see {@link Animator#hide} to keep it hidden). */
    public ArmaturePose hide(String joint) {
        int j = this.armature.jointIndex(joint);
        if (j >= 0) this.hidden[j] = true;
        return this;
    }
}
