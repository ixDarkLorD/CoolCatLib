package net.ixdarklord.coolcatcanvas.internal.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.FastColor;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * What later versions' {@code PoseStack} and {@code VertexConsumer} do that 1.20.1's don't: multiplying a pose by a
 * matrix while keeping its normals right, turning a normal into view, and writing a whole vertex with a packed colour.
 */
public final class Poses {
    private static final Matrix3f NORMAL = new Matrix3f();

    private Poses() {}

    /**
     * Multiplies the pose by a matrix, normals included: 1.20.1's {@code mulPoseMatrix} leaves the normal matrix as it
     * was, which lights anything rotated by the matrix as if it wasn't.
     */
    public static void mulPose(PoseStack poseStack, Matrix4f matrix) {
        PoseStack.Pose last = poseStack.last();
        last.pose().mul(matrix);
        // The inverse transpose keeps normals square to their faces under any scale; scaled back to a determinant of
        // one, as vanilla's own scale does, so they stay about unit length for code that doesn't normalise them.
        matrix.normal(NORMAL);
        float determinant = NORMAL.determinant();
        if (Math.abs(determinant) > 1.0E-12F) NORMAL.scale(1.0F / (float) Math.cbrt(Math.abs(determinant)));
        last.normal().mul(NORMAL);
    }

    /** A normal turned into the pose's view, unit length. */
    public static Vector3f transformNormal(PoseStack.Pose pose, float x, float y, float z, Vector3f out) {
        return pose.normal().transform(x, y, z, out).normalize();
    }

    /** Writes a whole vertex, its colour packed ARGB. */
    public static void vertex(VertexConsumer consumer, float x, float y, float z, int argb, float u, float v, int overlay, int light,
                              float normalX, float normalY, float normalZ) {
        consumer.vertex(x, y, z, FastColor.ARGB32.red(argb) / 255.0F, FastColor.ARGB32.green(argb) / 255.0F,
                FastColor.ARGB32.blue(argb) / 255.0F, FastColor.ARGB32.alpha(argb) / 255.0F, u, v, overlay, light, normalX, normalY, normalZ);
    }

    /** Packs a model's float colour (as {@code Model.renderToBuffer} takes it) into ARGB. */
    public static int argb(float red, float green, float blue, float alpha) {
        return FastColor.ARGB32.color((int) (alpha * 255.0F), (int) (red * 255.0F), (int) (green * 255.0F), (int) (blue * 255.0F));
    }
}
