package net.ixdarklord.coolcatcanvas.api.client.sound;

import net.minecraft.world.phys.Vec3;

import java.util.function.Function;

/**
 * Says how a sound comes through to the listener; registered with {@link SoundFilters#register}. It is asked about every
 * sound the game plays, music and the menus included ({@link SoundContext#relative()}), as it starts and as it moves.
 * It is asked on the sound thread, so it must be quick and read only state that is safe to read from there (an
 * immutable snapshot the game thread swaps in, for example).
 * <pre>{@code
 * SoundFilters.register(sound -> sound.source() == SoundSource.HOSTILE ? SoundFilter.muffled(0.5F) : SoundFilter.NONE);
 * SoundFilters.register(SoundFilterProvider.inWorld(position -> walls.filter(position)));
 * }</pre>
 */
@FunctionalInterface
public interface SoundFilterProvider {
    /** The filter for this sound, or {@link SoundFilter#NONE} to leave it be. */
    SoundFilter filter(SoundContext sound);

    /** A provider for the sounds at a place in the world only, by their place; music and the menus are left be. */
    static SoundFilterProvider inWorld(Function<Vec3, SoundFilter> filter) {
        return sound -> sound.relative() ? SoundFilter.NONE : filter.apply(sound.position());
    }
}
