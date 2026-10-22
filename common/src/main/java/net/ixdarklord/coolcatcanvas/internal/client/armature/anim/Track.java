package net.ixdarklord.coolcatcanvas.internal.client.armature.anim;

import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureContext;
import net.ixdarklord.coolcatcanvas.api.client.armature.molang.MolangExpr;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * The keyframes of one channel (rotation, position or scale) of one joint, three values each, as the file wrote them:
 * rotations in degrees and positions in pixels, in Bedrock's axes. Values may be Molang, read when sampled.
 */
public final class Track {
    // Scratch for sampling; tracks are only sampled on the render thread.
    private static final float[] VA = new float[3], VB = new float[3], VP = new float[3], VN = new float[3];

    private final Key[] keys;

    public Track(List<Key> keys) {
        this.keys = keys.toArray(Key[]::new);
        Arrays.sort(this.keys, Comparator.comparingDouble(Key::time));
    }

    /**
     * This track as a bend: amount, then direction. A keyframe written as one value (read as the same expression on
     * all three axes) is the amount alone, bending forward.
     */
    public Track bendShaped() {
        List<Key> keys = new java.util.ArrayList<>(this.keys.length);
        for (Key key : this.keys) {
            keys.add(new Key(key.time, bend(key.pre), bend(key.post), key.interp, key.bezierLeftTime, key.bezierLeftValue, key.bezierRightTime, key.bezierRightValue));
        }
        return new Track(keys);
    }

    private static MolangExpr[] bend(MolangExpr[] value) {
        if (value[1] != value[0] || value[2] != value[0]) return value;
        return new MolangExpr[]{value[0], MolangExpr.ZERO, MolangExpr.ZERO};
    }

    public boolean isEmpty() {
        return this.keys.length == 0;
    }

    /** The last keyframe's time, for clips that don't say how long they are. */
    public float lastTime() {
        return this.keys.length == 0 ? 0.0F : this.keys[this.keys.length - 1].time;
    }

    /** The three values at a time, into {@code out[0..2]}. */
    public void sample(double time, ArmatureContext context, float[] out) {
        Key[] keys = this.keys;
        int n = keys.length;
        if (n == 1) {
            keys[0].post(context, out);
            return;
        }
        if (time < keys[0].time) {
            keys[0].pre(context, out);
            return;
        }
        if (time >= keys[n - 1].time) {
            keys[n - 1].post(context, out);
            return;
        }
        int i = 0;
        while (i < n - 2 && keys[i + 1].time <= time) i++;
        Key a = keys[i], b = keys[i + 1];
        float span = b.time - a.time;
        float alpha = span <= 0.0F ? 1.0F : (float) ((time - a.time) / span);
        if (a.interp == Interp.STEP) {
            a.post(context, out);
            return;
        }
        float[] va = VA, vb = VB;
        a.post(context, va);
        b.pre(context, vb);
        if (a.interp == Interp.CATMULLROM || b.interp == Interp.CATMULLROM) {
            float[] vp = VP, vn = VN;
            if (i > 0) keys[i - 1].post(context, vp);
            else System.arraycopy(va, 0, vp, 0, 3);
            if (i + 2 < n) keys[i + 2].pre(context, vn);
            else System.arraycopy(vb, 0, vn, 0, 3);
            for (int axis = 0; axis < 3; axis++) out[axis] = catmullRom(alpha, vp[axis], va[axis], vb[axis], vn[axis]);
        } else if (a.interp == Interp.BEZIER || b.interp == Interp.BEZIER) {
            for (int axis = 0; axis < 3; axis++) {
                float rightTime = a.interp == Interp.BEZIER && a.bezierRightTime != null ? a.bezierRightTime[axis] : 0.0F;
                float rightValue = a.interp == Interp.BEZIER && a.bezierRightValue != null ? a.bezierRightValue[axis] : 0.0F;
                float leftTime = b.interp == Interp.BEZIER && b.bezierLeftTime != null ? b.bezierLeftTime[axis] : 0.0F;
                float leftValue = b.interp == Interp.BEZIER && b.bezierLeftValue != null ? b.bezierLeftValue[axis] : 0.0F;
                out[axis] = bezier((float) time, a.time, va[axis], a.time + rightTime, va[axis] + rightValue, b.time + leftTime, vb[axis] + leftValue, b.time, vb[axis]);
            }
        } else {
            for (int axis = 0; axis < 3; axis++) out[axis] = va[axis] + (vb[axis] - va[axis]) * alpha;
        }
    }

    private static float catmullRom(float t, float p0, float p1, float p2, float p3) {
        float t2 = t * t, t3 = t2 * t;
        return 0.5F * (2.0F * p1 + (-p0 + p2) * t + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * t2 + (-p0 + 3.0F * p1 - 3.0F * p2 + p3) * t3);
    }

    // A cubic Bezier through (t0,v0) and (t3,v3) with handles (t1,v1) and (t2,v2): find the curve parameter whose time
    // is the one asked for, then its value. The handles' times are kept inside the span so the curve never doubles back.
    private static float bezier(float time, float t0, float v0, float t1, float v1, float t2, float v2, float t3, float v3) {
        t1 = Math.max(t0, Math.min(t3, t1));
        t2 = Math.max(t0, Math.min(t3, t2));
        float lo = 0.0F, hi = 1.0F, s = 0.5F;
        for (int i = 0; i < 24; i++) {
            s = (lo + hi) * 0.5F;
            float at = cubic(s, t0, t1, t2, t3);
            if (Math.abs(at - time) < 1.0E-5F) break;
            if (at < time) lo = s;
            else hi = s;
        }
        return cubic(s, v0, v1, v2, v3);
    }

    private static float cubic(float s, float p0, float p1, float p2, float p3) {
        float u = 1.0F - s;
        return u * u * u * p0 + 3.0F * u * u * s * p1 + 3.0F * u * s * s * p2 + s * s * s * p3;
    }

    public enum Interp {
        LINEAR, CATMULLROM, BEZIER, STEP;

        public static Interp byName(@Nullable String name) {
            if (name == null) return LINEAR;
            return switch (name) {
                case "catmullrom", "smooth" -> CATMULLROM;
                case "bezier" -> BEZIER;
                case "step" -> STEP;
                default -> LINEAR;
            };
        }
    }

    /**
     * One keyframe. {@code pre} is the value coming in and {@code post} going out; they differ only where the curve
     * jumps. Bezier handles are offsets from the keyframe, per axis, in seconds and in the channel's units.
     */
    public record Key(float time, MolangExpr[] pre, MolangExpr[] post, Interp interp,
                      float @Nullable [] bezierLeftTime, float @Nullable [] bezierLeftValue,
                      float @Nullable [] bezierRightTime, float @Nullable [] bezierRightValue) {
        public static Key of(float time, MolangExpr[] value, Interp interp) {
            return new Key(time, value, value, interp, null, null, null, null);
        }

        void pre(ArmatureContext context, float[] out) {
            for (int axis = 0; axis < 3; axis++) out[axis] = this.pre[axis].evalFloat(context);
        }

        void post(ArmatureContext context, float[] out) {
            for (int axis = 0; axis < 3; axis++) out[axis] = this.post[axis].evalFloat(context);
        }
    }
}
