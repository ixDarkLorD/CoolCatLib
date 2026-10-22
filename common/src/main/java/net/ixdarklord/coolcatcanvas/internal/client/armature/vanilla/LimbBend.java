package net.ixdarklord.coolcatcanvas.internal.client.armature.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.List;

/**
 * Bends a vanilla limb the way an elbow or knee bends: a smooth arc through the middle of its length, the end beyond it
 * turned whole. The part above the bend stays put, the bend itself is a curved stretch (not a fold between two rigid
 * halves), so the limb keeps its outline with no gap, crease or overlap, and the texture follows round the curve.
 *
 * <p>Only the faces running along the limb are cut, into thin slices through the bend; the ends are moved whole. All
 * of it happens as the part is drawn, with no new model and nothing allocated, so armor, sleeves and anything else
 * drawn on the same part bend the same way when given the same bend.</p>
 *
 * <p>Geometry: the limb runs down its Y (vanilla's Y points down, so from shoulder to hand); a point {@code s} toward
 * the bend direction and {@code y} along the limb, within the bend {@code [a, b]}, goes round an arc of radius
 * {@code R - s} (with {@code R = (b - a) / angle}) through {@code (y - a) / (b - a)} of the angle. That keeps the limb's
 * centre line its own length, squeezes the inside of the bend and stretches the outside, as skin does.</p>
 */
public final class LimbBend {
    // Slices across the bend: enough for a smooth curve at any angle a limb bends to.
    private static final int SLICES = 6;
    private static final float[] LEVELS = new float[SLICES + 3];
    private static final Vector3f POSITION = new Vector3f();
    private static final Vector3f NORMAL = new Vector3f();
    private static final Vector3f OUT = new Vector3f();
    private static final float[] CORNER = new float[5];

    private LimbBend() {
    }

    /** The bend's stretch along a part: its middle third, from its cubes' extent along Y. */
    public static float[] zone(ModelPart part) {
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (ModelPart.Cube cube : BendablePart.of(part).coolcatlib$cubes()) {
            min = Math.min(min, cube.minY);
            max = Math.max(max, cube.maxY);
        }
        if (min > max) return new float[]{0.0F, 0.0F};
        float middle = (min + max) * 0.5F, half = Math.max(0.5F, (max - min) / 6.0F);
        return new float[]{middle - half, middle + half};
    }

    // ---- Drawing ----

    /** Draws a bent part's cubes; called in place of vanilla's drawing of them. */
    public static void draw(BendablePart bend, PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, int color) {
        float amount = bend.coolcatlib$bendAmount(), direction = bend.coolcatlib$bendDirection();
        float a = bend.coolcatlib$bendStart(), b = bend.coolcatlib$bendEnd();
        float dx = (float) Math.sin(direction), dz = (float) -Math.cos(direction);
        Matrix4f matrix = pose.pose();
        for (ModelPart.Cube cube : bend.coolcatlib$cubes()) {
            for (ModelPart.Polygon polygon : cube.polygons) {
                ModelPart.Vertex[] corners = polygon.vertices();
                int along = alongEdge(corners);
                if (along < 0) {
                    // An end face (or one lying across the limb): moved whole, by where it is along the limb.
                    float y = corners[0].y();
                    float angle = angleAt(y, amount, a, b);
                    turnNormal(polygon.normal(), angle, dx, dz, pose);
                    for (ModelPart.Vertex corner : corners) {
                        emit(corner.x(), corner.y(), corner.z(), corner.u(), corner.v(), amount, a, b, dx, dz, matrix, buffer, light, overlay, color);
                    }
                    continue;
                }
                // A side face: edges A (corners[along] -> [along+1]) and B (the opposite one, same way), cut into slices.
                ModelPart.Vertex a0 = corners[along], a1 = corners[(along + 1) & 3];
                ModelPart.Vertex b0 = corners[(along + 3) & 3], b1 = corners[(along + 2) & 3];
                int levels = levels(Math.min(a0.y(), a1.y()), Math.max(a0.y(), a1.y()), a, b);
                boolean down = a1.y() > a0.y();
                for (int i = 0; i + 1 < levels; i++) {
                    float from = down ? LEVELS[i] : LEVELS[levels - 1 - i];
                    float to = down ? LEVELS[i + 1] : LEVELS[levels - 2 - i];
                    float t0 = (from - a0.y()) / (a1.y() - a0.y()), t1 = (to - a0.y()) / (a1.y() - a0.y());
                    turnNormal(polygon.normal(), angleAt((from + to) * 0.5F, amount, a, b), dx, dz, pose);
                    emitBetween(a0, a1, t0, amount, a, b, dx, dz, matrix, buffer, light, overlay, color);
                    emitBetween(a0, a1, t1, amount, a, b, dx, dz, matrix, buffer, light, overlay, color);
                    emitBetween(b0, b1, t1, amount, a, b, dx, dz, matrix, buffer, light, overlay, color);
                    emitBetween(b0, b1, t0, amount, a, b, dx, dz, matrix, buffer, light, overlay, color);
                }
            }
        }
    }

    // The index of a corner whose edge to the next runs along the limb (changes in Y), or -1 for a face across it.
    private static int alongEdge(ModelPart.Vertex[] corners) {
        if (corners.length != 4) return -1;
        for (int i = 0; i < 4; i++) {
            ModelPart.Vertex from = corners[i], to = corners[(i + 1) & 3];
            if (Math.abs(to.y() - from.y()) > 1.0E-4F && Math.abs(to.x() - from.x()) < 1.0E-4F && Math.abs(to.z() - from.z()) < 1.0E-4F) return i;
        }
        return -1;
    }

    // Where to cut a face running from y0 to y1: its ends, the bend's ends, and slices through the bend; sorted.
    private static int levels(float y0, float y1, float a, float b) {
        int count = 0;
        LEVELS[count++] = y0;
        float from = Math.max(y0, a), to = Math.min(y1, b);
        if (from < to) {
            for (int i = 0; i <= SLICES; i++) {
                float y = from + (to - from) * i / SLICES;
                if (y > LEVELS[count - 1] + 1.0E-4F && y < y1 - 1.0E-4F) LEVELS[count++] = y;
            }
        }
        LEVELS[count++] = y1;
        return count;
    }

    private static void emitBetween(ModelPart.Vertex from, ModelPart.Vertex to, float t, float amount, float a, float b, float dx, float dz,
                                    Matrix4f matrix, VertexConsumer buffer, int light, int overlay, int color) {
        float[] c = CORNER;
        c[0] = from.x() + (to.x() - from.x()) * t;
        c[1] = from.y() + (to.y() - from.y()) * t;
        c[2] = from.z() + (to.z() - from.z()) * t;
        c[3] = from.u() + (to.u() - from.u()) * t;
        c[4] = from.v() + (to.v() - from.v()) * t;
        emit(c[0], c[1], c[2], c[3], c[4], amount, a, b, dx, dz, matrix, buffer, light, overlay, color);
    }

    private static void emit(float x, float y, float z, float u, float v, float amount, float a, float b, float dx, float dz,
                             Matrix4f matrix, VertexConsumer buffer, int light, int overlay, int color) {
        bendPoint(x, y, z, amount, a, b, dx, dz, POSITION);
        Vector3f at = matrix.transformPosition(POSITION.x / 16.0F, POSITION.y / 16.0F, POSITION.z / 16.0F, OUT);
        buffer.addVertex(at.x, at.y, at.z, color, u, v, overlay, light, NORMAL.x, NORMAL.y, NORMAL.z);
    }

    // ---- The bend itself ----

    /** How far the limb has turned at a point along it. */
    static float angleAt(float y, float amount, float a, float b) {
        if (y <= a) return 0.0F;
        if (y >= b || b <= a) return amount;
        return amount * (y - a) / (b - a);
    }

    /** Where a point of the straight limb (pixels, the part's own space) goes when it bends. */
    static void bendPoint(float x, float y, float z, float amount, float a, float b, float dx, float dz, Vector3f out) {
        if (y <= a || Math.abs(amount) < 1.0E-5F) {
            out.set(x, y, z);
            return;
        }
        float s = x * dx + z * dz;
        float qx = x - s * dx, qz = z - s * dz;
        float length = b - a, radius = length / amount;
        float newY, newS;
        if (y < b) {
            float angle = amount * (y - a) / length;
            newY = a + (radius - s) * (float) Math.sin(angle);
            newS = radius - (radius - s) * (float) Math.cos(angle);
        } else {
            float sin = (float) Math.sin(amount), cos = (float) Math.cos(amount), beyond = y - b;
            newY = a + (radius - s) * sin + beyond * cos;
            newS = radius - (radius - s) * cos + beyond * sin;
        }
        out.set(qx + newS * dx, newY, qz + newS * dz);
    }

    // A face's normal turned by the angle the limb has at it, then into view; left in NORMAL.
    private static void turnNormal(Vector3fc normal, float angle, float dx, float dz, PoseStack.Pose pose) {
        float ny = normal.y(), ns = normal.x() * dx + normal.z() * dz;
        float qx = normal.x() - ns * dx, qz = normal.z() - ns * dz;
        float sin = (float) Math.sin(angle), cos = (float) Math.cos(angle);
        float y = ny * cos - ns * sin, s = ny * sin + ns * cos;
        pose.transformNormal(qx + s * dx, y, qz + s * dz, NORMAL);
    }

    /**
     * Moves a pose stack at a bent part's pivot to where its far end has gone, so what hangs at the end (an item in a
     * hand) follows the bend. Nothing is done for a straight part.
     */
    public static void followEnd(BendablePart bend, PoseStack poseStack) {
        float amount = bend.coolcatlib$bendAmount();
        if (Math.abs(amount) < 1.0E-5F) return;
        float a = bend.coolcatlib$bendStart(), b = bend.coolcatlib$bendEnd(), direction = bend.coolcatlib$bendDirection();
        float dx = (float) Math.sin(direction), dz = (float) -Math.cos(direction);
        // Beyond the bend the limb is turned whole: about the axis across it, through a point the arc gives.
        Vector3f origin = new Vector3f();
        bendPoint(0.0F, b, 0.0F, amount, a, b, dx, dz, origin);
        poseStack.translate(origin.x / 16.0F, origin.y / 16.0F, origin.z / 16.0F);
        // Turning by the angle round the axis across the limb (Y x direction) carries Y toward the direction.
        Vector3f axis = new Vector3f(0.0F, 1.0F, 0.0F).cross(dx, 0.0F, dz).normalize();
        poseStack.rotate(new org.joml.Quaternionf().rotationAxis(amount, axis));
        poseStack.translate(0.0F, -b / 16.0F, 0.0F);
    }

    /** Bends a part and everything on it (sleeves, pants: layers made as its children) the same way. */
    public static void bendTree(ModelPart part, float amount, float direction, float start, float end, List<ModelPart> touched) {
        BendablePart.of(part).coolcatlib$setBend(amount, direction, start, end);
        touched.add(part);
        for (ModelPart child : ((net.ixdarklord.coolcatcanvas.internal.mixin.client.ModelPartAccessor) (Object) part).coolcatlib$children().values()) {
            bendTree(child, amount, direction, start, end, touched);
        }
    }
}
