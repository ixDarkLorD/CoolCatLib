package net.ixdarklord.coolcatcanvas.api.client.sound;

import net.ixdarklord.coolcatcanvas.internal.client.sound.SoundFilterManager;

/**
 * Muffles and roughens the game's sounds as the registered {@link SoundFilterProvider}s say: a player hiding in a
 * chest hears the world through its walls, one under a spell hears it from far away. Each sound with a place in the
 * world gets the providers' filters combined ({@link SoundFilter#then}), as a low-pass filter on its OpenAL source and
 * as much of it as they say sent, muffled the same, through a distortion. Music and the menus, which play relative to
 * the listener, are never filtered.
 * <pre>{@code
 * SoundFilters.register(position -> hidden ? new SoundFilter(0.7F, 0.15F) : SoundFilter.NONE);
 * hidden = true;
 * SoundFilters.refresh();      // the sounds already playing change too
 * }</pre>
 * A sound is filtered when it starts and each time it moves; {@link #refresh()} filters every sound playing again, for
 * when the providers' answers change. Without OpenAL's EFX extension (rare: the game's own OpenAL Soft has it) sounds
 * are left as they are.
 */
public final class SoundFilters {
    private SoundFilters() {}

    /** Adds a provider; its filter is combined with the others'. */
    public static void register(SoundFilterProvider provider) {
        SoundFilterManager.register(provider);
    }

    /** Filters every sound playing again. Called from the game thread; the work is done on the sound thread. */
    public static void refresh() {
        SoundFilterManager.refresh();
    }
}
