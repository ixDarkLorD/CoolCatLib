package net.ixdarklord.coolcatcanvas.api.client.vfx;

import net.minecraft.world.phys.Vec3;

/**
 * Draws shapes of one {@link VfxMaterial} into the world for the current frame, from a {@link VfxRenderer}. Positions
 * are world coordinates; colours are {@code 0xAARRGGBB}. Nothing here lasts: draw again every frame.
 */
public interface VfxDraw {
    /**
     * A square facing the camera.
     *
     * @param halfSize half its width, in blocks
     * @param roll     its turn around the line of sight, in radians
     */
    VfxDraw billboard(double x, double y, double z, float halfSize, float roll, int argb);

    default VfxDraw billboard(Vec3 position, float halfSize, int argb) {
        return this.billboard(position.x, position.y, position.z, halfSize, 0.0F, argb);
    }

    /**
     * A ribbon from one point to another, turned to face the camera: a beam, a bolt, a tether. The texture runs
     * along it, its left edge at {@code from}.
     *
     * @param width the ribbon's full width, in blocks
     */
    VfxDraw beam(Vec3 from, Vec3 to, float width, int argb);

    /** A beam whose colour changes along it. */
    VfxDraw beam(Vec3 from, Vec3 to, float width, int fromArgb, int toArgb);

    /**
     * A flat quad through four corners, seen from both sides: a rune on the ground, a wall of light. The texture's
     * corners go to the points in the order top-left, bottom-left, bottom-right, top-right.
     */
    VfxDraw quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int argb);

    /**
     * A flat quad with UVs of the caller's choosing, for materials with a {@linkplain VfxMaterial#withShader shader}:
     * the shader sees {@code (u0, v0)} at {@code a} and {@code (u1, v1)} at {@code c}. UVs may run past 0 to 1.
     */
    VfxDraw quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, float u0, float v0, float u1, float v1, int argb);

    /**
     * A square lying flat, facing up: a circle on the ground.
     *
     * @param halfSize half its width, in blocks
     * @param yaw      its turn around the vertical, in radians
     */
    VfxDraw flat(double x, double y, double z, float halfSize, float yaw, int argb);

    /**
     * The ribbon a {@link Trail} leaves, turned to face the camera, from its newest point to its oldest.
     *
     * @param width    the ribbon's full width in blocks along its length: the curve's start is at the newest point
     * @param headArgb the colour at the newest point
     * @param tailArgb the colour at the oldest point
     */
    VfxDraw trail(Trail trail, Curve width, int headArgb, int tailArgb);
}
