package net.ixdarklord.coolcatcanvas.api.client.sound;

import net.ixdarklord.coolcatcanvas.internal.client.sound.SoundFilterManager;
import net.minecraft.resources.ResourceLocation;

/**
 * Muffles, roughens and echoes the game's sounds: a player hiding in a chest hears the world through its walls, one
 * under a spell hears it from far away, a cave rings. Every sound gets the filters below combined
 * ({@link SoundFilter#then}): a low-pass filter on its OpenAL source, and as much of it as they say sent, filtered the
 * same, through a distortion and into a reverb.
 * <p>
 * Two ways to filter:
 * <ul>
 *   <li><b>Providers</b> ({@link #register}) decide sound by sound, by where it is, what it is or its category.</li>
 *   <li><b>Named filters</b> ({@link #set}) apply to every sound but the menus' clicks, music included, and fade in and
 *   out on their own. The server can set them too ({@code api.sound.SoundFilterControl}).</li>
 * </ul>
 * <pre>{@code
 * SoundFilters.register(SoundFilterProvider.inWorld(position -> hidden ? SoundFilter.muffled(0.8F) : SoundFilter.NONE));
 * SoundFilters.refresh();                                                   // after `hidden` changed
 *
 * SoundFilters.set(MyMod.id("underwater"), new SoundFilter(0.6F, 0.1F), 20);  // fades in over a second
 * SoundFilters.clear(MyMod.id("underwater"), 40);                            // and out over two
 * }</pre>
 * A sound is filtered when it starts and each time it moves; named filters re-filter what is playing as they fade, and
 * {@link #refresh()} does it for when a provider's answers change. Without OpenAL's EFX extension (rare: the game's own
 * OpenAL Soft has it) sounds are left as they are.
 */
public final class SoundFilters {
    private SoundFilters() {}

    /** A registered provider, to remove again. */
    @FunctionalInterface
    public interface Registration {
        /** Stops asking the provider, and filters what is playing again without it. */
        void remove();
    }

    /** Adds a provider; its filter is combined with the others'. */
    public static Registration register(SoundFilterProvider provider) {
        return SoundFilterManager.register(provider);
    }

    /** Filters every sound playing again. Called from the game thread; the work is done on the sound thread. */
    public static void refresh() {
        SoundFilterManager.refresh();
    }

    /** Applies a named filter to every sound at once, replacing any of that name. */
    public static void set(ResourceLocation id, SoundFilter filter) {
        set(id, filter, 0);
    }

    /**
     * Fades a named filter in over {@code fadeTicks}, replacing any of that name (a filter already in fades from where
     * it is to the new one).
     */
    public static void set(ResourceLocation id, SoundFilter filter, int fadeTicks) {
        SoundFilterManager.set(id, filter, fadeTicks);
    }

    /** Takes a named filter off at once. */
    public static void clear(ResourceLocation id) {
        clear(id, 0);
    }

    /** Fades a named filter out over {@code fadeTicks}, then removes it. */
    public static void clear(ResourceLocation id, int fadeTicks) {
        SoundFilterManager.clear(id, fadeTicks);
    }

    /** Whether a named filter is on (fading out still counts). */
    public static boolean isSet(ResourceLocation id) {
        return SoundFilterManager.isSet(id);
    }
}
