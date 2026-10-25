package net.ixdarklord.coolcatcanvas.api.client.sound;

import net.minecraft.world.phys.Vec3;

/**
 * Says how a sound at a place in the world comes through to the listener; registered with
 * {@link SoundFilters#register}. It is asked on the sound thread, for each sound as it starts and as it moves, so it
 * must be quick and read only state that is safe to read from there (an immutable snapshot the game thread swaps in,
 * for example).
 */
@FunctionalInterface
public interface SoundFilterProvider {
    /** The filter for a sound at this place, or {@link SoundFilter#NONE} to leave it be. */
    SoundFilter filter(Vec3 position);
}
