package net.ixdarklord.coolcatcanvas.internal.client.vfx;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.ixdarklord.coolcatcanvas.api.client.vfx.Curve;
import net.ixdarklord.coolcatcanvas.api.client.vfx.Trail;
import net.ixdarklord.coolcatcanvas.api.client.vfx.VfxDraw;
import net.ixdarklord.coolcatcanvas.api.client.vfx.VfxMaterial;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Arrays;

// Everything drawn with one material in a frame: quads gathered as plain floats (positions already relative to the
// camera), then written out as triangles in one run so the batch is a single draw. Reused across frames; it only grows.
final class VfxBatch implements VfxDraw {
    // Per quad: four corners of x, y, z, u, v, then four colours (their bits kept in floats).
    private static final int FLOATS_PER_QUAD = 4 * 5 + 4;
    static final int VERTICES_PER_QUAD = 6;

    final VfxMaterial material;
    final VfxUniform uniform = new VfxUniform();
    private final VfxCamera camera;
    private float[] data = new float[FLOATS_PER_QUAD * 64];
    private int quads;
    int firstVertex;

    private final Vector3f side = new Vector3f();
    private final Vector3f tangent = new Vector3f();

    VfxBatch(VfxMaterial material, VfxCamera camera) {
        this.material = material;
        this.camera = camera;
    }

    int quads() {
        return this.quads;
    }

    void clear() {
        this.quads = 0;
    }

    /** Writes the batch's quads as triangles. */
    void emit(BufferBuilder builder) {
        for (int quad = 0; quad < this.quads; quad++) {
            int base = quad * FLOATS_PER_QUAD;
            this.vertex(builder, base, 0);
            this.vertex(builder, base, 1);
            this.vertex(builder, base, 2);
            this.vertex(builder, base, 0);
            this.vertex(builder, base, 2);
            this.vertex(builder, base, 3);
        }
    }

    private void vertex(BufferBuilder builder, int base, int corner) {
        int at = base + corner * 5;
        builder.addVertex(this.data[at], this.data[at + 1], this.data[at + 2])
                .setUv(this.data[at + 3], this.data[at + 4])
                .setColor(Float.floatToRawIntBits(this.data[base + 20 + corner]));
    }

    // ---- Raw quads, in camera-relative coordinates ----

    private int reserve() {
        int base = this.quads * FLOATS_PER_QUAD;
        if (base + FLOATS_PER_QUAD > this.data.length) this.data = Arrays.copyOf(this.data, this.data.length * 2);
        this.quads++;
        return base;
    }

    private void corner(int base, int corner, float x, float y, float z, float u, float v, int argb) {
        int at = base + corner * 5;
        this.data[at] = x;
        this.data[at + 1] = y;
        this.data[at + 2] = z;
        this.data[at + 3] = u;
        this.data[at + 4] = v;
        this.data[base + 20 + corner] = Float.intBitsToFloat(argb);
    }

    /** A square around a camera-relative point, facing the camera. */
    void billboardRelative(float x, float y, float z, float halfSize, float roll, int argb) {
        if (ARGB.alpha(argb) == 0 || halfSize <= 0.0F) return;
        float cos = Mth.cos(roll) * halfSize;
        float sin = Mth.sin(roll) * halfSize;
        VfxCamera c = this.camera;
        float rx = c.rightX * cos + c.upX * sin, ry = c.rightY * cos + c.upY * sin, rz = c.rightZ * cos + c.upZ * sin;
        float ux = c.upX * cos - c.rightX * sin, uy = c.upY * cos - c.rightY * sin, uz = c.upZ * cos - c.rightZ * sin;
        int base = this.reserve();
        this.corner(base, 0, x - rx + ux, y - ry + uy, z - rz + uz, 0.0F, 0.0F, argb);
        this.corner(base, 1, x - rx - ux, y - ry - uy, z - rz - uz, 0.0F, 1.0F, argb);
        this.corner(base, 2, x + rx - ux, y + ry - uy, z + rz - uz, 1.0F, 1.0F, argb);
        this.corner(base, 3, x + rx + ux, y + ry + uy, z + rz + uz, 1.0F, 0.0F, argb);
    }

    /** A ribbon between two camera-relative points, turned to face the camera. */
    void beamRelative(float ax, float ay, float az, float bx, float by, float bz, float width, int fromArgb, int toArgb) {
        if (width <= 0.0F || (ARGB.alpha(fromArgb) == 0 && ARGB.alpha(toArgb) == 0)) return;
        // Sideways is across both the beam and the line of sight to it (the camera is at the origin).
        this.tangent.set(bx - ax, by - ay, bz - az);
        this.side.set((ax + bx) * 0.5F, (ay + by) * 0.5F, (az + bz) * 0.5F).cross(this.tangent);
        if (this.side.lengthSquared() < 1.0E-12F) return;
        this.side.normalize(width * 0.5F);
        float sx = this.side.x, sy = this.side.y, sz = this.side.z;
        int base = this.reserve();
        this.corner(base, 0, ax + sx, ay + sy, az + sz, 0.0F, 0.0F, fromArgb);
        this.corner(base, 1, ax - sx, ay - sy, az - sz, 0.0F, 1.0F, fromArgb);
        this.corner(base, 2, bx - sx, by - sy, bz - sz, 1.0F, 1.0F, toArgb);
        this.corner(base, 3, bx + sx, by + sy, bz + sz, 1.0F, 0.0F, toArgb);
    }

    // ---- VfxDraw: world coordinates ----

    @Override
    public VfxDraw billboard(double x, double y, double z, float halfSize, float roll, int argb) {
        this.billboardRelative(this.camera.relX(x), this.camera.relY(y), this.camera.relZ(z), halfSize, roll, argb);
        return this;
    }

    @Override
    public VfxDraw beam(Vec3 from, Vec3 to, float width, int argb) {
        return this.beam(from, to, width, argb, argb);
    }

    @Override
    public VfxDraw beam(Vec3 from, Vec3 to, float width, int fromArgb, int toArgb) {
        VfxCamera c = this.camera;
        this.beamRelative(c.relX(from.x), c.relY(from.y), c.relZ(from.z), c.relX(to.x), c.relY(to.y), c.relZ(to.z), width, fromArgb, toArgb);
        return this;
    }

    @Override
    public VfxDraw quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int argb) {
        return this.quad(a, b, c, d, 0.0F, 0.0F, 1.0F, 1.0F, argb);
    }

    @Override
    public VfxDraw quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, float u0, float v0, float u1, float v1, int argb) {
        if (ARGB.alpha(argb) == 0) return this;
        VfxCamera cam = this.camera;
        int base = this.reserve();
        this.corner(base, 0, cam.relX(a.x), cam.relY(a.y), cam.relZ(a.z), u0, v0, argb);
        this.corner(base, 1, cam.relX(b.x), cam.relY(b.y), cam.relZ(b.z), u0, v1, argb);
        this.corner(base, 2, cam.relX(c.x), cam.relY(c.y), cam.relZ(c.z), u1, v1, argb);
        this.corner(base, 3, cam.relX(d.x), cam.relY(d.y), cam.relZ(d.z), u1, v0, argb);
        return this;
    }

    @Override
    public VfxDraw flat(double x, double y, double z, float halfSize, float yaw, int argb) {
        if (ARGB.alpha(argb) == 0 || halfSize <= 0.0F) return this;
        float cx = this.camera.relX(x), cy = this.camera.relY(y), cz = this.camera.relZ(z);
        float cos = Mth.cos(yaw) * halfSize;
        float sin = Mth.sin(yaw) * halfSize;
        int base = this.reserve();
        this.corner(base, 0, cx - cos - sin, cy, cz + sin - cos, 0.0F, 0.0F, argb);
        this.corner(base, 1, cx - cos + sin, cy, cz + sin + cos, 0.0F, 1.0F, argb);
        this.corner(base, 2, cx + cos + sin, cy, cz - sin + cos, 1.0F, 1.0F, argb);
        this.corner(base, 3, cx + cos - sin, cy, cz - sin - cos, 1.0F, 0.0F, argb);
        return this;
    }

    @Override
    public VfxDraw trail(Trail trail, Curve width, int headArgb, int tailArgb) {
        int points = trail.size();
        if (points < 2) return this;
        VfxCamera c = this.camera;
        float lastX = 0.0F, lastY = 0.0F, lastZ = 0.0F, lastSx = 0.0F, lastSy = 0.0F, lastSz = 0.0F, lastU = 0.0F;
        int lastColor = 0;
        boolean hasLast = false;
        for (int i = 0; i < points; i++) {
            // 0 at the oldest point, 1 at the newest.
            float along = i / (float) (points - 1);
            float x = c.relX(trail.coordinate(i, 0)), y = c.relY(trail.coordinate(i, 1)), z = c.relZ(trail.coordinate(i, 2));
            int before = Math.max(i - 1, 0);
            int after = Math.min(i + 1, points - 1);
            this.tangent.set(
                    (float) (trail.coordinate(after, 0) - trail.coordinate(before, 0)),
                    (float) (trail.coordinate(after, 1) - trail.coordinate(before, 1)),
                    (float) (trail.coordinate(after, 2) - trail.coordinate(before, 2)));
            this.side.set(x, y, z).cross(this.tangent);
            float half = width.at(1.0F - along) * 0.5F;
            if (this.side.lengthSquared() < 1.0E-12F || half <= 0.0F) this.side.zero();
            else this.side.normalize(half);
            int color = ARGB.srgbLerp(along, tailArgb, headArgb);
            if (hasLast) {
                int base = this.reserve();
                this.corner(base, 0, lastX + lastSx, lastY + lastSy, lastZ + lastSz, lastU, 0.0F, lastColor);
                this.corner(base, 1, lastX - lastSx, lastY - lastSy, lastZ - lastSz, lastU, 1.0F, lastColor);
                this.corner(base, 2, x - this.side.x, y - this.side.y, z - this.side.z, along, 1.0F, color);
                this.corner(base, 3, x + this.side.x, y + this.side.y, z + this.side.z, along, 0.0F, color);
            }
            lastX = x;
            lastY = y;
            lastZ = z;
            lastSx = this.side.x;
            lastSy = this.side.y;
            lastSz = this.side.z;
            lastU = along;
            lastColor = color;
            hasLast = true;
        }
        return this;
    }
}
