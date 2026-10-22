package net.ixdarklord.coolcatcanvas.internal.client.armature;

import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureContext;
import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureEvent;
import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureFrame;
import net.ixdarklord.coolcatcanvas.api.client.vfx.VfxEffects;
import net.ixdarklord.coolcatcanvas.api.vfx.VfxParams;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Plays sound and particle keyframes by themselves: a sound effect naming a sound event, a particle effect naming a
 * world effect ({@code VfxEffects}) or a simple particle type. Names that are neither (Bedrock's own particle effects,
 * say) are left to the animator's listeners.
 * Items play none: an item in a slot has nowhere to play them.
 */
public final class ArmatureEffects {
    private ArmatureEffects() {
    }

    public static void play(ArmatureEvent event, @Nullable ArmatureFrame frame) {
        ArmatureContext context = event.context();
        if (!context.stack().isEmpty()) return;
        Level level = context.level();
        Identifier id = Identifier.tryParse(event.effect());
        if (level == null || id == null) return;
        switch (event.kind()) {
            case SOUND -> {
                Vec3 at = context.position();
                Entity entity = context.entity();
                SoundSource source = entity instanceof Enemy ? SoundSource.HOSTILE : entity != null ? SoundSource.NEUTRAL : SoundSource.BLOCKS;
                level.playLocalSound(at.x, at.y, at.z, SoundEvent.createVariableRangeEvent(id), source, 1.0F, 1.0F, false);
            }
            case PARTICLE -> {
                Vec3 at = context.position();
                if (event.locator() != null && frame != null) {
                    Vector3f point = frame.worldPosition(event.locator());
                    if (point != null) at = new Vec3(point.x, point.y, point.z);
                }
                // A world effect by that name first (VfxEffects), then a vanilla particle.
                if (VfxEffects.play(id, VfxParams.at(at))) return;
                ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.getValue(id);
                if (type instanceof SimpleParticleType simple) level.addParticle(simple, at.x, at.y, at.z, 0.0, 0.0, 0.0);
            }
            default -> {
            }
        }
    }
}
