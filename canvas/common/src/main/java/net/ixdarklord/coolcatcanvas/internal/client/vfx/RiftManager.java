package net.ixdarklord.coolcatcanvas.internal.client.vfx;

import net.ixdarklord.coolcatcanvas.api.client.camera.CameraShake;
import net.ixdarklord.coolcatcanvas.api.client.vfx.Curve;
import net.ixdarklord.coolcatcanvas.api.client.vfx.Rift;
import net.ixdarklord.coolcatcanvas.api.client.vfx.RiftSpec;
import net.ixdarklord.coolcatcanvas.api.client.vfx.VfxDraw;
import net.ixdarklord.coolcatcanvas.api.client.vfx.VfxFrame;
import net.ixdarklord.coolcatcanvas.api.client.vfx.VfxMaterial;
import net.ixdarklord.coolcatcanvas.api.client.vfx.WorldParticles;
import net.ixdarklord.coolcatcanvas.api.client.vfx.WorldRifts;
import net.ixdarklord.coolcatcanvas.api.client.vfx.WorldVfx;
import net.ixdarklord.coolcatcanvas.api.utils.Easing;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

// Rifts: their three acts (open, stay, close), their sounds, motes and flashes, and the quad each is drawn on. The
// look itself is vfx/rift.fsh. That shader has no uniforms of its own, so what differs from rift to rift travels in
// what a quad already carries: its colour, and its UVs. The slit always fills the same part of UV space; a rift that
// is half open is drawn with UVs that run twice as far, which makes the slit half as wide on the same quad.
public final class RiftManager {
    private static final VfxMaterial MATERIAL = VfxMaterial.alpha(VfxMaterial.GLOW).withSoftness(0.35F).withShader(VfxMaterial.RIFT_SHADER);
    private static final VfxMaterial FLASH = VfxMaterial.additive(VfxMaterial.GLOW);
    private static final VfxMaterial RING = VfxMaterial.additive(VfxMaterial.RING).withSoftness(0.2F);
    /** The quad is this much larger than the slit, to leave room for its glow. */
    private static final float MARGIN = 1.7F;
    private static final double DRAW_RANGE_SQR = 160.0 * 160.0;

    private static final Map<ResourceLocation, RiftSpec> SPECS = new HashMap<>();
    private static final Map<UUID, Instance> RIFTS = new LinkedHashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private RiftManager() {}

    public static void init() {
        SPECS.put(WorldRifts.DEFAULT, RiftSpec.DEFAULT);
        VfxManager.register(CoolCatCanvas.rl("rifts"), RiftManager::render);
    }

    public static void register(ResourceLocation id, RiftSpec spec) {
        SPECS.put(id, spec);
    }

    public static @Nullable RiftSpec spec(ResourceLocation id) {
        return SPECS.get(id);
    }

    public static @Nullable Rift get(UUID id) {
        return RIFTS.get(id);
    }

    public static void clear() {
        RIFTS.clear();
    }

    public static Rift open(UUID id, RiftSpec spec, Vec3 position, int openTicks) {
        Instance existing = RIFTS.get(id);
        if (existing != null) return existing;
        Instance rift = new Instance(spec, position, openTicks, RANDOM.nextFloat() * 100.0F);
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            rift.phase = Rift.Phase.GONE;
            return rift;
        }
        RIFTS.put(id, rift);
        // The first act: a flash where the air gives, the tearing sound, and a jolt for whoever stands near.
        int color = 0xFF000000 | spec.color();
        WorldVfx.flash(FLASH, position, Curve.of(0.2F, spec.height() * 0.9F, 0.0F).easing(Easing.EXPO_OUT, Easing.SINE_IN_OUT), color, Math.max(6, spec.openTicks() / 2));
        play(level, spec.openSound(), position, spec.volume(), 0.9F + RANDOM.nextFloat() * 0.2F);
        if (spec.shake() > 0.0F) CameraShake.shake(position, spec.height() * 6.0F, spec.shake(), spec.openTicks());
        return rift;
    }

    /** The server asked for a registered rift. */
    public static void openNamed(UUID id, ResourceLocation specId, Vec3 position, int openTicks) {
        RiftSpec spec = SPECS.get(specId);
        if (spec == null) {
            CoolCatCanvas.LOGGER.debug("Asked to open unknown rift {}", specId);
            return;
        }
        open(id, spec, position, openTicks);
    }

    public static void close(UUID id) {
        Instance rift = RIFTS.get(id);
        if (rift != null) rift.close();
    }

    private static void play(ClientLevel level, @Nullable SoundEvent sound, Vec3 at, float volume, float pitch) {
        if (sound != null) level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.AMBIENT, volume, pitch, false);
    }

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            RIFTS.clear();
            return;
        }
        if (minecraft.isPaused() || RIFTS.isEmpty()) return;
        RIFTS.values().removeIf(rift -> {
            rift.tick(level);
            return rift.phase == Rift.Phase.GONE;
        });
    }

    private static void render(VfxFrame frame) {
        if (RIFTS.isEmpty()) return;
        Vec3 camera = frame.cameraPosition();
        VfxDraw draw = frame.draw(MATERIAL);
        for (Instance rift : RIFTS.values()) {
            Vec3 at = rift.position;
            if (at.distanceToSqr(camera) > DRAW_RANGE_SQR) continue;
            float open = rift.openness(frame.partialTick());
            if (open <= 0.004F) continue;
            RiftSpec spec = rift.spec;
            // Upright, turned about the vertical to face the camera (a tear has no thin side to be caught from), unless
            // it is held facing one way.
            double dx = rift.facing != null ? rift.facing.x : camera.x - at.x, dz = rift.facing != null ? rift.facing.z : camera.z - at.z;
            double flat = Math.sqrt(dx * dx + dz * dz);
            double rx = flat < 1.0E-4 ? 1.0 : -dz / flat, rz = flat < 1.0E-4 ? 0.0 : dx / flat;
            float halfHeight = spec.height() * 0.5F * MARGIN;
            // The quad is as wide as it is tall, so the shader's space is square; the slit's own width is in the UVs.
            double halfWidth = halfHeight;
            Vec3 topLeft = new Vec3(at.x - rx * halfWidth, at.y + halfHeight, at.z - rz * halfWidth);
            Vec3 bottomLeft = new Vec3(at.x - rx * halfWidth, at.y - halfHeight, at.z - rz * halfWidth);
            Vec3 bottomRight = new Vec3(at.x + rx * halfWidth, at.y - halfHeight, at.z + rz * halfWidth);
            Vec3 topRight = new Vec3(at.x + rx * halfWidth, at.y + halfHeight, at.z + rz * halfWidth);
            // Across: the slit is `aspect` of the height wide when fully open, and narrower by how open it is.
            float aspect = spec.width() / spec.height();
            float across = MARGIN / (aspect * Math.max(open, 0.02F));
            // Along: it grows to its full height in the first part of opening, and keeps it while it narrows.
            float along = MARGIN / Mth.clamp(open * 2.5F, 0.05F, 1.0F);
            int alpha = Math.round(255.0F * Mth.clamp(open * 6.0F, 0.0F, 1.0F));
            draw.quad(topLeft, bottomLeft, bottomRight, topRight, -across, -along, across, along, VfxManager.argb(alpha, spec.color()));
        }
    }

    private static final class Instance implements Rift {
        final RiftSpec spec;
        final Vec3 position;
        final int holdTicks;
        final float seed;
        @Nullable Vec3 facing;
        Phase phase = Phase.OPENING;
        int age;
        int phaseAge;

        Instance(RiftSpec spec, Vec3 position, int holdTicks, float seed) {
            this.spec = spec;
            this.position = position;
            this.holdTicks = holdTicks;
            this.seed = seed;
        }

        void tick(ClientLevel level) {
            this.age++;
            this.phaseAge++;
            switch (this.phase) {
                case OPENING -> {
                    if (this.phaseAge >= this.spec.openTicks()) this.enter(Phase.OPEN);
                }
                case OPEN -> {
                    if (this.phaseAge % this.spec.ambientTicks() == 1) {
                        play(level, this.spec.ambientSound(), this.position, this.spec.volume() * 0.6F, 0.8F + RANDOM.nextFloat() * 0.4F);
                    }
                    this.motes();
                    if (this.holdTicks >= 0 && this.phaseAge >= this.holdTicks) this.close();
                }
                case CLOSING -> {
                    if (this.phaseAge >= this.spec.closeTicks()) {
                        // The last act ends with a snap: a ring thrown out from the line it closed to.
                        int color = 0xFF000000 | this.spec.color();
                        WorldVfx.flash(RING, this.position, Curve.of(0.1F, this.spec.height() * 0.8F).easing(Easing.EXPO_OUT), color, 10);
                        WorldVfx.flash(FLASH, this.position, Curve.of(this.spec.height() * 0.5F, 0.0F), color, 6);
                        this.phase = Phase.GONE;
                    }
                }
                case GONE -> {
                }
            }
        }

        // Motes appear around the rift and fall into its middle.
        private void motes() {
            if (this.spec.particles() == null) return;
            float rate = this.spec.particleRate();
            int count = Mth.floor(rate) + (RANDOM.nextFloat() < rate - Mth.floor(rate) ? 1 : 0);
            for (int i = 0; i < count; i++) {
                double angle = RANDOM.nextDouble() * Math.PI * 2.0;
                double reach = this.spec.height() * (0.45 + RANDOM.nextDouble() * 0.35);
                double x = this.position.x + Math.cos(angle) * reach;
                double y = this.position.y + (RANDOM.nextDouble() - 0.5) * this.spec.height();
                double z = this.position.z + Math.sin(angle) * reach;
                double speed = 0.05 + RANDOM.nextDouble() * 0.04;
                Vec3 pull = this.position.subtract(x, y, z).normalize().scale(speed);
                WorldParticles.spawn(this.spec.particles(), x, y, z, pull.x, pull.y, pull.z);
            }
        }

        private void enter(Phase next) {
            this.phase = next;
            this.phaseAge = 0;
        }

        @Override
        public RiftSpec spec() {
            return this.spec;
        }

        @Override
        public Vec3 position() {
            return this.position;
        }

        @Override
        public Phase phase() {
            return this.phase;
        }

        @Override
        public float openness(float partialTick) {
            float time = this.phaseAge + partialTick;
            return switch (this.phase) {
                // It overshoots a little and settles, like something forced.
                case OPENING -> Easing.BACK_OUT.apply(Mth.clamp(time / this.spec.openTicks(), 0.0F, 1.0F));
                case OPEN -> 1.0F + 0.04F * Mth.sin((this.age + partialTick) * 0.11F);
                case CLOSING -> 1.0F - Easing.CUBIC_IN.apply(Mth.clamp(time / this.spec.closeTicks(), 0.0F, 1.0F));
                case GONE -> 0.0F;
            };
        }

        @Override
        public void close() {
            if (this.phase != Phase.OPENING && this.phase != Phase.OPEN) return;
            this.enter(Phase.CLOSING);
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) play(level, this.spec.closeSound(), this.position, this.spec.volume(), 0.9F + RANDOM.nextFloat() * 0.2F);
            if (this.spec.shake() > 0.0F) CameraShake.shake(this.position, this.spec.height() * 6.0F, this.spec.shake() * 0.7F, this.spec.closeTicks());
        }

        @Override
        public void remove() {
            this.phase = Phase.GONE;
        }

        @Override
        public Rift facing(@Nullable Vec3 toward) {
            this.facing = toward == null || toward.horizontalDistanceSqr() < 1.0E-8 ? null : new Vec3(toward.x, 0.0, toward.z).normalize();
            return this;
        }
    }
}
