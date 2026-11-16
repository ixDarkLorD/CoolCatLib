package net.ixdarklord.coolcatcanvas.api.client.armature;

import net.ixdarklord.coolcatcanvas.api.client.vfx.ParticleSpec;
import net.ixdarklord.coolcatcanvas.api.client.vfx.WorldParticles;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * Effect particles ({@link ParticleSpec}) pouring from a point of an armature: fire from a dragon's mouth, sparks
 * from a forge's chimney, dust from a digging claw. They leave the way the point faces (its -Z, the model's front at
 * rest) at {@link #speed}, scattered by {@link #spread}, then live on their own.
 * <pre>{@code
 * animator.emitter("mouth", FIRE)
 *         .rate(60).speed(0.35F).spread(0.08F)
 *         .when(ctx -> animator.layer("action").isPlaying("breathe_fire"));
 * }</pre>
 * Items in slots and hands have no place in the world, so they emit nothing.
 */
public final class ArmatureEmitter {
    private final String point;
    private final ParticleSpec spec;
    private float rate = 20.0F;
    private float speed = 0.1F;
    private float spread = 0.05F;
    private @Nullable Predicate<ArmatureContext> active;
    private double carried;

    ArmatureEmitter(String point, ParticleSpec spec) {
        this.point = point;
        this.spec = spec;
    }

    public String point() {
        return this.point;
    }

    /** Particles a second; 20 by default. */
    public ArmatureEmitter rate(float perSecond) {
        this.rate = Math.max(0.0F, perSecond);
        return this;
    }

    /** How fast they leave, in blocks a tick, along the point's facing; 0.1 by default. */
    public ArmatureEmitter speed(float blocksPerTick) {
        this.speed = blocksPerTick;
        return this;
    }

    /** Random speed added in any direction, in blocks a tick; 0.05 by default. */
    public ArmatureEmitter spread(float blocksPerTick) {
        this.spread = Math.max(0.0F, blocksPerTick);
        return this;
    }

    /** Emits only while this holds, asked every frame; always by default. */
    public ArmatureEmitter when(Predicate<ArmatureContext> active) {
        this.active = active;
        return this;
    }

    void emit(ArmatureFrame frame, ArmatureContext context, double deltaTime) {
        if (this.active != null && !this.active.test(context)) {
            this.carried = 0.0;
            return;
        }
        this.carried += this.rate * deltaTime;
        if (this.carried < 1.0) return;
        Matrix4f point = frame.pointMatrix(this.point);
        if (point == null) return;
        Matrix4f world = new Matrix4f(frame.modelToWorld()).mul(point);
        Vector3f at = world.getTranslation(new Vector3f());
        Vector3f facing = world.transformDirection(new Vector3f(0.0F, 0.0F, -1.0F));
        if (facing.lengthSquared() > 1.0E-8F) facing.normalize(this.speed);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        while (this.carried >= 1.0) {
            this.carried -= 1.0;
            WorldParticles.spawn(this.spec, at.x, at.y, at.z,
                    facing.x + (random.nextFloat() * 2.0F - 1.0F) * this.spread,
                    facing.y + (random.nextFloat() * 2.0F - 1.0F) * this.spread,
                    facing.z + (random.nextFloat() * 2.0F - 1.0F) * this.spread);
        }
    }
}
