package net.ixdarklord.coolcatcanvas.api.client.vfx;

import net.ixdarklord.coolcatcanvas.internal.client.vfx.VfxManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Spawns effect particles ({@link ParticleSpec}) in the world the client is in. Call from the client thread: a client
 * tick, a packet handler, or a {@link VfxEffect}.
 * <pre>{@code
 * WorldParticles.spawn(EMBER, x, y, z, 0.0, 0.05, 0.0);
 * WorldParticles.burst(EMBER, pos, 24, 0.2, 0.12);     // 24 of them, flying apart
 * }</pre>
 * Particles are dropped when the client leaves the world. At most {@link #MAX} live at once; past that, new ones are
 * not spawned.
 */
public final class WorldParticles {
    public static final int MAX = 16384;
    private static final RandomSource RANDOM = RandomSource.create();

    private WorldParticles() {}

    /** One particle at a position, moving by a velocity in blocks a tick. */
    public static void spawn(ParticleSpec spec, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
        VfxManager.spawn(spec, x, y, z, velocityX, velocityY, velocityZ);
    }

    public static void spawn(ParticleSpec spec, Vec3 position, Vec3 velocity) {
        VfxManager.spawn(spec, position.x, position.y, position.z, velocity.x, velocity.y, velocity.z);
    }

    /**
     * Several particles around a point, each flying off in its own direction.
     *
     * @param spread how far from the point, in blocks, they may start
     * @param speed  the most they move, in blocks a tick; each gets a random share of it
     */
    public static void burst(ParticleSpec spec, Vec3 position, int count, double spread, double speed) {
        for (int i = 0; i < count; i++) {
            // A random direction, even over the sphere.
            double y = RANDOM.nextDouble() * 2.0 - 1.0;
            double angle = RANDOM.nextDouble() * Math.PI * 2.0;
            double flat = Math.sqrt(1.0 - y * y);
            double dx = Math.cos(angle) * flat;
            double dz = Math.sin(angle) * flat;
            double offset = RANDOM.nextDouble() * spread;
            double velocity = (0.35 + RANDOM.nextDouble() * 0.65) * speed;
            VfxManager.spawn(spec, position.x + dx * offset, position.y + y * offset, position.z + dz * offset, dx * velocity, y * velocity, dz * velocity);
        }
    }

    /**
     * Particles strung along a line, each drifting a little: a beam's dust, a bolt's wake.
     *
     * @param jitter the most each moves, in blocks a tick, in a random direction
     */
    public static void line(ParticleSpec spec, Vec3 from, Vec3 to, int count, double jitter) {
        for (int i = 0; i < count; i++) {
            double t = count == 1 ? 0.5 : (i + RANDOM.nextDouble()) / count;
            VfxManager.spawn(spec, from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t, from.z + (to.z - from.z) * t,
                    (RANDOM.nextDouble() - 0.5) * 2.0 * jitter, (RANDOM.nextDouble() - 0.5) * 2.0 * jitter, (RANDOM.nextDouble() - 0.5) * 2.0 * jitter);
        }
    }

    /** How many particles are alive now. */
    public static int count() {
        return VfxManager.particleCount();
    }

    public static void clear() {
        VfxManager.clearParticles();
    }
}
