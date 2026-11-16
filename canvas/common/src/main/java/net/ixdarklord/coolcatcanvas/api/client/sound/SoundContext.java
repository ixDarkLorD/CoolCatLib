package net.ixdarklord.coolcatcanvas.api.client.sound;

import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A sound a {@link SoundFilterProvider} is asked about.
 *
 * @param position where it plays: a place in the world, or, for a sound {@code relative} to the listener, its offset
 *                 from them
 * @param sound    the sound event it plays (as {@code minecraft:entity.zombie.ambient}), or null if not known yet
 * @param source   its sound category, the one its volume slider is for
 * @param relative whether it plays relative to the listener (music, the menus, a few of the game's own) instead of at a
 *                 place in the world
 */
public record SoundContext(Vec3 position, @Nullable Identifier sound, SoundSource source, boolean relative) {
    /** Whether it plays at a place in the world. */
    public boolean inWorld() {
        return !this.relative;
    }
}
