package net.ixdarklord.coolcatcanvas.api.client.armature;

import org.jetbrains.annotations.Nullable;

/**
 * Something a clip did at a keyframe: a Blockbench sound, particle or timeline keyframe, or a Bedrock
 * {@code sound_effects}, {@code particle_effects} or {@code timeline} entry.
 *
 * <p>Sounds whose effect names a sound event ({@code "minecraft:entity.wolf.growl"}) and particles whose effect names
 * a simple particle type ({@code "minecraft:flame"}) are played by CoolCatLib without any code; listeners get them as
 * well, to do something else or more. An instruction's Molang (for example {@code v.step = v.step + 1;}) has already
 * run when listeners get it.</p>
 *
 * @param effect  the sound or particle name, or the instruction's text
 * @param locator the point a particle comes out of; null for the model's origin
 * @param time    when in the clip it happened, in seconds
 */
public record ArmatureEvent(Kind kind, String layer, String clip, String effect, @Nullable String locator, float time, ArmatureContext context) {
    public enum Kind { SOUND, PARTICLE, INSTRUCTION }

    @FunctionalInterface
    public interface Listener {
        void onEvent(ArmatureEvent event);
    }
}
