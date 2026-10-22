package net.ixdarklord.coolcatcanvas.api.vfx;

import net.minecraft.world.phys.Vec3;

/**
 * What the server tells a client about one playing of a world effect. What each field means is up to the effect; the
 * usual reading is given here.
 *
 * @param position where it happens
 * @param target   a second point: where a beam ends, or the direction a burst flies (as a point offset from
 *                 {@code position}); equal to {@code position} when unused
 * @param color    its colour, as {@code 0xRRGGBB}
 * @param scale    its size or strength, 1 being the effect's normal one
 */
public record VfxParams(Vec3 position, Vec3 target, int color, float scale) {
    public static VfxParams at(Vec3 position) {
        return new VfxParams(position, position, 0xFFFFFF, 1.0F);
    }

    public static VfxParams at(Vec3 position, int color) {
        return new VfxParams(position, position, color, 1.0F);
    }

    public static VfxParams between(Vec3 from, Vec3 to, int color) {
        return new VfxParams(from, to, color, 1.0F);
    }

    public VfxParams withScale(float scale) {
        return new VfxParams(this.position, this.target, this.color, scale);
    }

    public VfxParams withColor(int color) {
        return new VfxParams(this.position, this.target, color, this.scale);
    }

    /** From {@code position} towards {@code target}; zero when they are the same point. */
    public Vec3 direction() {
        Vec3 delta = this.target.subtract(this.position);
        return delta.lengthSqr() < 1.0E-8 ? Vec3.ZERO : delta.normalize();
    }
}
