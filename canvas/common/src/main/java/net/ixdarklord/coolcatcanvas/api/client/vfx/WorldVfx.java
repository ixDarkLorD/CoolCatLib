package net.ixdarklord.coolcatcanvas.api.client.vfx;

import net.ixdarklord.coolcatcanvas.internal.client.vfx.VfxManager;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * World effects: glowing shapes drawn in the world, after everything else in it and before the player's hand. They
 * are lit by nothing, blend over or add to the scene ({@link VfxBlend}), hide behind solid things and fade softly
 * where they meet them ({@link VfxMaterial#softness()}).
 * <p>
 * Three ways in:
 * <ul>
 *     <li>{@link WorldParticles}: particles that live on their own once spawned.</li>
 *     <li>{@link #register}: a {@link VfxRenderer} called every frame, to draw beams, trails and shapes that follow
 *     something.</li>
 *     <li>{@link #beam} and {@link #flash}: one-line effects that draw themselves and fade.</li>
 * </ul>
 * Effects started by the server go through {@link VfxEffects}.
 */
public final class WorldVfx {
    private WorldVfx() {}

    /** Calls a renderer every frame a world is drawn, until it's unregistered. Registering an id again replaces it. */
    public static void register(Identifier id, VfxRenderer renderer) {
        VfxManager.register(id, renderer);
    }

    public static void unregister(Identifier id) {
        VfxManager.unregister(id);
    }

    /**
     * A beam that appears at once and fades away over some ticks, narrowing as it goes.
     *
     * @param width its full width at the start, in blocks
     */
    public static void beam(VfxMaterial material, Vec3 from, Vec3 to, float width, int argb, int ticks) {
        VfxManager.addBeam(material, from, to, width, argb, ticks);
    }

    /**
     * A camera-facing shape that swells and fades over some ticks: a flash, or with {@link VfxMaterial#RING} a
     * shockwave.
     *
     * @param size half its width over its life, in blocks
     */
    public static void flash(VfxMaterial material, Vec3 position, Curve size, int argb, int ticks) {
        VfxManager.addFlash(material, position, size, argb, ticks, false);
    }

    /** Like {@link #flash}, lying flat on the ground instead of facing the camera. */
    public static void groundFlash(VfxMaterial material, Vec3 position, Curve size, int argb, int ticks) {
        VfxManager.addFlash(material, position, size, argb, ticks, true);
    }
}
