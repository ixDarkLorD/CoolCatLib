package net.ixdarklord.coolcatcanvas.internal.client.armature;

import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureMaterial;
import org.jetbrains.annotations.Nullable;

/**
 * An armature's faces, ready to draw: one group per texture and material (each group is one draw), and in each group the
 * faces of each joint in a flat array. A face is four corners of x, y, z (model pixels, at rest), u, v (0 to 1), then
 * its normal: {@link #STRIDE} floats. Triangles repeat their last corner.
 */
public final class ArmatureMesh {
    public static final int STRIDE = 23;
    public static final ArmatureMesh EMPTY = new ArmatureMesh(new Group[0]);

    private final Group[] groups;

    public ArmatureMesh(Group[] groups) {
        this.groups = groups;
    }

    public Group[] groups() {
        return this.groups;
    }

    /**
     * @param texture  the armature's texture slot
     * @param byJoint  each joint's faces, null where a joint has none in this group
     * @param skin     cloth: for the joints of a sheet, each face's corners weighed between joints ({@link ArmatureCloth});
     *                 null where a joint is drawn rigidly, or null altogether
     */
    public record Group(int texture, ArmatureMaterial material, float[][] byJoint, float @Nullable [][] skin) {
        public Group(int texture, ArmatureMaterial material, float[][] byJoint) {
            this(texture, material, byJoint, null);
        }

        /** Floats of skin per face. */
        public static final int SKIN_FACE = ArmatureCloth.FACE;
        /** Floats of skin per corner: influences of (joint, weight, x, y, z). */
        public static final int SKIN_CORNER = ArmatureCloth.CORNER;
    }
}
