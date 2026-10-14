package net.ixdarklord.coolcatcanvas.api.client.vfx;

import net.ixdarklord.coolcatcanvas.api.utils.Easing;
import net.minecraft.resources.Identifier;

/**
 * What a kind of effect particle looks like and how it lives, built once and spawned many times with
 * {@link WorldParticles}. Immutable.
 * <pre>{@code
 * static final ParticleSpec EMBER = ParticleSpec.builder(VfxMaterial.additive(VfxMaterial.GLOW))
 *         .lifetime(20, 35)
 *         .size(Curve.of(0.12F, 0.2F, 0.0F).easing(Easing.EXPO_OUT, Easing.SINE_IN))
 *         .opacity(Curve.FADE)
 *         .color(0xFFD27A, 0xFF4A1C)
 *         .gravity(-0.004F)      // drifts up
 *         .drag(0.94F)
 *         .build();
 * }</pre>
 * These are not vanilla particles: they aren't registered, don't exist on the server and ignore the Particles video
 * setting. They are drawn after the whole world, so glows show through water and clouds.
 */
public final class ParticleSpec {
    private final VfxMaterial material;
    private final Shape shape;
    private final int minLifetime;
    private final int maxLifetime;
    private final Curve size;
    private final Curve opacity;
    private final int startColor;
    private final int endColor;
    private final Easing colorEasing;
    private final Curve spin;
    private final boolean randomRoll;
    private final float gravity;
    private final float drag;
    private final float stretch;

    private ParticleSpec(Builder builder) {
        this.material = builder.material;
        this.shape = builder.shape;
        this.minLifetime = builder.minLifetime;
        this.maxLifetime = builder.maxLifetime;
        this.size = builder.size;
        this.opacity = builder.opacity;
        this.startColor = builder.startColor;
        this.endColor = builder.endColor;
        this.colorEasing = builder.colorEasing;
        this.spin = builder.spin;
        this.randomRoll = builder.randomRoll;
        this.gravity = builder.gravity;
        this.drag = builder.drag;
        this.stretch = builder.stretch;
    }

    public static Builder builder(VfxMaterial material) {
        return new Builder(material);
    }

    public VfxMaterial material() {
        return this.material;
    }

    public Shape shape() {
        return this.shape;
    }

    public int minLifetime() {
        return this.minLifetime;
    }

    public int maxLifetime() {
        return this.maxLifetime;
    }

    public Curve size() {
        return this.size;
    }

    public Curve opacity() {
        return this.opacity;
    }

    public int startColor() {
        return this.startColor;
    }

    public int endColor() {
        return this.endColor;
    }

    public Easing colorEasing() {
        return this.colorEasing;
    }

    public Curve spin() {
        return this.spin;
    }

    public boolean randomRoll() {
        return this.randomRoll;
    }

    public float gravity() {
        return this.gravity;
    }

    public float drag() {
        return this.drag;
    }

    public float stretch() {
        return this.stretch;
    }

    /** How a particle's quad is laid in the world. */
    public enum Shape {
        /** Always facing the camera. */
        BILLBOARD,
        /** A streak along the particle's motion, longer the faster it moves, turned to face the camera. */
        SPARK
    }

    public static final class Builder {
        private final VfxMaterial material;
        private Shape shape = Shape.BILLBOARD;
        private int minLifetime = 20;
        private int maxLifetime = 20;
        private Curve size = Curve.constant(0.1F);
        private Curve opacity = Curve.FADE;
        private int startColor = 0xFFFFFF;
        private int endColor = 0xFFFFFF;
        private Easing colorEasing = Easing.LINEAR;
        private Curve spin = Curve.ZERO;
        private boolean randomRoll;
        private float gravity;
        private float drag = 1.0F;
        private float stretch = 2.0F;

        private Builder(VfxMaterial material) {
            this.material = material;
        }

        public Builder shape(Shape shape) {
            this.shape = shape;
            return this;
        }

        /** How long it lives, in ticks. */
        public Builder lifetime(int ticks) {
            return this.lifetime(ticks, ticks);
        }

        /** Each particle lives a random number of ticks in this range, so a burst doesn't vanish all at once. */
        public Builder lifetime(int minTicks, int maxTicks) {
            if (minTicks < 1 || maxTicks < minTicks) throw new IllegalArgumentException("Bad lifetime: " + minTicks + " to " + maxTicks);
            this.minLifetime = minTicks;
            this.maxLifetime = maxTicks;
            return this;
        }

        /** Half the width of its quad over its life, in blocks. A {@link Shape#SPARK}'s is its thickness. */
        public Builder size(Curve size) {
            this.size = size;
            return this;
        }

        public Builder size(float size) {
            return this.size(Curve.constant(size));
        }

        /** Its opacity over its life, 0 to 1. */
        public Builder opacity(Curve opacity) {
            this.opacity = opacity;
            return this;
        }

        /** One colour for its whole life, as {@code 0xRRGGBB}. */
        public Builder color(int rgb) {
            return this.color(rgb, rgb);
        }

        /**
         * A colour that shifts over its life, as {@code 0xRRGGBB}. The shift runs around the colour wheel, so it
         * stays vivid on the way instead of passing through grey.
         */
        public Builder color(int startRgb, int endRgb) {
            this.startColor = startRgb & 0xFFFFFF;
            this.endColor = endRgb & 0xFFFFFF;
            return this;
        }

        public Builder colorEasing(Easing easing) {
            this.colorEasing = easing;
            return this;
        }

        /** How fast it turns over its life, in radians a tick. Each particle turns one way or the other at random. */
        public Builder spin(Curve radiansPerTick) {
            this.spin = radiansPerTick;
            return this;
        }

        /** Starts each particle at a random angle. */
        public Builder randomRoll() {
            this.randomRoll = true;
            return this;
        }

        /** Blocks a tick it speeds up downwards, each tick. Negative to rise. */
        public Builder gravity(float gravity) {
            this.gravity = gravity;
            return this;
        }

        /** What its speed is multiplied by each tick: 1 keeps it, 0.9 slows it quickly. */
        public Builder drag(float drag) {
            if (drag < 0.0F || drag > 1.0F) throw new IllegalArgumentException("Drag must be 0 to 1: " + drag);
            this.drag = drag;
            return this;
        }

        /** For a {@link Shape#SPARK}: how many ticks of motion its length covers. */
        public Builder stretch(float ticks) {
            this.stretch = ticks;
            return this;
        }

        public ParticleSpec build() {
            return new ParticleSpec(this);
        }
    }
}
