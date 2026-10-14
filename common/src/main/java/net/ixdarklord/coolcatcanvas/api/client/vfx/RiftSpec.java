package net.ixdarklord.coolcatcanvas.api.client.vfx;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

/**
 * What a rift looks and sounds like: a tear in the air that opens, hangs there, and closes. See {@link WorldRifts}.
 *
 * @param width         its width at its widest, in blocks
 * @param height        its height, in blocks
 * @param color         the colour of its edge and glow, {@code 0xRRGGBB}; its inside is a dark shade of the same
 * @param openTicks     how long it takes to tear open
 * @param closeTicks    how long it takes to close
 * @param openSound     played as it opens; null for none
 * @param ambientSound  played over and over while it stays open; null for none
 * @param closeSound    played as it closes; null for none
 * @param ambientTicks  ticks between two plays of the ambient sound
 * @param volume        how loud its sounds are
 * @param particles     motes drawn into it while it is open; null for none
 * @param particleRate  motes a tick
 * @param shake         how hard the camera of a player beside it shakes as it opens and as it closes, in degrees;
 *                      0 for none
 */
public record RiftSpec(float width, float height, int color, int openTicks, int closeTicks,
                       @Nullable SoundEvent openSound, @Nullable SoundEvent ambientSound, @Nullable SoundEvent closeSound,
                       int ambientTicks, float volume, @Nullable ParticleSpec particles, float particleRate, float shake) {
    /** A violet rift two blocks wide and four tall, with vanilla's portal sounds. */
    public static final RiftSpec DEFAULT = builder().build();

    public RiftSpec {
        if (width <= 0.0F || height <= 0.0F) throw new IllegalArgumentException("A rift needs a size: " + width + " by " + height);
        if (openTicks < 1 || closeTicks < 1) throw new IllegalArgumentException("Opening and closing take at least a tick");
        if (ambientTicks < 1) throw new IllegalArgumentException("Ambient interval must be at least a tick");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private float width = 2.0F;
        private float height = 4.0F;
        private int color = 0xB070FF;
        private int openTicks = 24;
        private int closeTicks = 16;
        private @Nullable SoundEvent openSound = SoundEvents.END_PORTAL_SPAWN;
        private @Nullable SoundEvent ambientSound = SoundEvents.PORTAL_AMBIENT;
        private @Nullable SoundEvent closeSound = SoundEvents.ENDER_EYE_DEATH;
        private int ambientTicks = 70;
        private float volume = 1.0F;
        private @Nullable ParticleSpec particles = defaultParticles(0xB070FF);
        private boolean ownParticles;
        private float particleRate = 0.6F;
        private float shake = 1.2F;

        private Builder() {}

        public Builder size(float width, float height) {
            this.width = width;
            this.height = height;
            return this;
        }

        /** The edge and glow colour. The default motes take it too, unless {@link #particles} set others. */
        public Builder color(int rgb) {
            this.color = rgb & 0xFFFFFF;
            return this;
        }

        public Builder timing(int openTicks, int closeTicks) {
            this.openTicks = openTicks;
            this.closeTicks = closeTicks;
            return this;
        }

        /** The three sounds; any may be null. */
        public Builder sounds(@Nullable SoundEvent open, @Nullable SoundEvent ambient, @Nullable SoundEvent close) {
            this.openSound = open;
            this.ambientSound = ambient;
            this.closeSound = close;
            return this;
        }

        public Builder silent() {
            return this.sounds(null, null, null);
        }

        public Builder ambientEvery(int ticks) {
            this.ambientTicks = ticks;
            return this;
        }

        public Builder volume(float volume) {
            this.volume = volume;
            return this;
        }

        /** The motes drawn into the open rift, and how many a tick; null for none. */
        public Builder particles(@Nullable ParticleSpec particles, float perTick) {
            this.particles = particles;
            this.particleRate = perTick;
            this.ownParticles = true;
            return this;
        }

        public Builder shake(float degrees) {
            this.shake = degrees;
            return this;
        }

        public RiftSpec build() {
            ParticleSpec motes = this.ownParticles ? this.particles : defaultParticles(this.color);
            return new RiftSpec(this.width, this.height, this.color, this.openTicks, this.closeTicks, this.openSound, this.ambientSound, this.closeSound,
                    this.ambientTicks, this.volume, motes, this.particleRate, this.shake);
        }

        private static ParticleSpec defaultParticles(int rgb) {
            return ParticleSpec.builder(VfxMaterial.additive(VfxMaterial.SPARKLE))
                    .lifetime(14, 26)
                    .size(Curve.of(0.0F, 0.11F, 0.0F))
                    .opacity(Curve.FADE)
                    .color(0xFFFFFF, rgb)
                    .drag(0.94F)
                    .build();
        }
    }
}
