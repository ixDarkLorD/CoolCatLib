package net.ixdarklord.coolcatcanvas.api.client.armature;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * An armature posed for one frame: where every joint is, which are shown, and how they are tinted. Renderers draw it; code
 * that hangs things on an armature or spawns particles from it reads joint and point places from it.
 *
 * <p>Joint matrices take model pixels at rest to model pixels posed. {@link #modelToWorld()} goes on from there to the
 * world (blocks), as last drawn.</p>
 */
public final class ArmatureFrame {
    final BakedArmature armature;
    final Matrix4f[] joints;
    final Matrix3f[] normals;
    final boolean[] visible;
    final int[] tint;
    final boolean[] glow;
    final Matrix4f modelToWorld = new Matrix4f();
    final List<ObjProp> props = new ArrayList<>();

    ArmatureFrame(BakedArmature armature) {
        int n = armature.jointCount();
        this.armature = armature;
        this.joints = new Matrix4f[n];
        this.normals = new Matrix3f[n];
        for (int j = 0; j < n; j++) {
            this.joints[j] = new Matrix4f();
            this.normals[j] = new Matrix3f();
        }
        this.visible = new boolean[n];
        this.tint = new int[n];
        this.glow = new boolean[n];
    }

    public BakedArmature armature() {
        return this.armature;
    }

    public Matrix4fc joint(int index) {
        return this.joints[index];
    }

    public @Nullable Matrix4fc joint(String name) {
        int index = this.armature.jointIndex(name);
        return index < 0 ? null : this.joints[index];
    }

    /** The matrix's normal part, for lighting. */
    public Matrix3f normal(int index) {
        return this.normals[index];
    }

    public boolean visible(int index) {
        return this.visible[index];
    }

    /** The ARGB colour a joint's faces are multiplied by (white when untinted). */
    public int tint(int index) {
        return this.tint[index];
    }

    /** Whether a joint is drawn at full brightness whatever the light. */
    public boolean glows(int index) {
        return this.glow[index];
    }

    /** The {@code .obj} models carried this frame: the armature's own and the animator's. */
    public List<ObjProp> props() {
        return this.props;
    }

    /** The joint a point (or joint) of that name moves with, or -1. */
    public int pointJoint(String name) {
        ArmaturePoint point = this.armature.point(name);
        return point != null ? point.joint() : this.armature.jointIndex(name);
    }

    /** From model pixels to the world, as of the last update. */
    public Matrix4fc modelToWorld() {
        return this.modelToWorld;
    }

    /**
     * Where a point (or, if there is no point by that name, a joint's pivot) is, as a matrix in model pixels: its
     * position, and its axes turned as it is. Null if the armature has neither.
     */
    public @Nullable Matrix4f pointMatrix(String name) {
        ArmaturePoint point = this.armature.point(name);
        if (point != null) {
            Matrix4f matrix = new Matrix4f(this.joints[point.joint()]).translate(point.position().x(), point.position().y(), point.position().z());
            return matrix.rotateZYX(point.rotation().z(), point.rotation().y(), point.rotation().x());
        }
        Joint joint = this.armature.joint(name);
        if (joint == null) return null;
        return new Matrix4f(this.joints[joint.index()]).translate(joint.pivot().x(), joint.pivot().y(), joint.pivot().z());
    }

    /** Where a point or joint pivot is in the world, in blocks; null if the armature has neither. */
    public @Nullable Vector3f worldPosition(String name) {
        Matrix4f matrix = this.pointMatrix(name);
        if (matrix == null) return null;
        return this.modelToWorld.transformPosition(matrix.getTranslation(new Vector3f()));
    }
}
