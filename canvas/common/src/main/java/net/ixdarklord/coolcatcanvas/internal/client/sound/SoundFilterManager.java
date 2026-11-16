package net.ixdarklord.coolcatcanvas.internal.client.sound;

import net.ixdarklord.coolcatcanvas.api.client.sound.SoundContext;
import net.ixdarklord.coolcatcanvas.api.client.sound.SoundFilter;
import net.ixdarklord.coolcatcanvas.api.client.sound.SoundFilterProvider;
import net.ixdarklord.coolcatcanvas.api.client.sound.SoundFilters;
import net.ixdarklord.coolcatcanvas.internal.mixin.client.SoundEngineAccessor;
import net.ixdarklord.coolcatcanvas.internal.mixin.client.SoundManagerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The sound filters' work ({@code api.client.sound.SoundFilters}): for each sound, the providers' filters and the named
 * ones combined, as an EFX low-pass filter on its OpenAL source, and as much of it as they say sent, filtered the same,
 * through a distortion (auxiliary send 0) and into a reverb (send 1). Applied by {@code ChannelMixin} when a sound starts
 * and as it moves.
 */
public final class SoundFilterManager {
    private static final List<SoundFilterProvider> PROVIDERS = new CopyOnWriteArrayList<>();
    // The named filters, game thread only, and what they come to together, which the sound thread reads.
    private static final Map<ResourceLocation, Named> NAMED = new LinkedHashMap<>();
    private static volatile SoundFilter named = SoundFilter.NONE;
    // The distortion: the edge it gives (0 to 1), the band it works on, and the highs it keeps.
    private static final float DISTORTION_EDGE = 0.3F;
    private static final float DISTORTION_EQ_CENTER = 700.0F;
    private static final float DISTORTION_EQ_BANDWIDTH = 2000.0F;
    private static final float DISTORTION_LOWPASS = 3000.0F;
    // The reverb: a large stone space, its tail a little under three seconds.
    private static final float REVERB_DECAY = 2.8F;
    private static final float REVERB_DENSITY = 1.0F;
    private static final float REVERB_DIFFUSION = 0.9F;
    // The filter objects (one for the sound itself, one for each send), set for each sound before they are attached (a
    // source keeps a copy of a filter's settings); the effects' slots; and the OpenAL context they belong to: a new one
    // (the audio device changed) needs them made again. Sound thread only.
    private static int filter;
    private static int distortionFilter;
    private static int reverbFilter;
    private static int distortionSlot;
    private static int reverbSlot;
    private static boolean reverbSupported;
    private static long filterContext;
    private static boolean supported;

    private SoundFilterManager() {}

    public static SoundFilters.Registration register(SoundFilterProvider provider) {
        PROVIDERS.add(provider);
        return () -> {
            if (PROVIDERS.remove(provider)) refresh();
        };
    }

    public static void refresh() {
        SoundManagerAccessor manager = (SoundManagerAccessor) Minecraft.getInstance().getSoundManager();
        ((SoundEngineAccessor) manager.coolcatlib$soundEngine()).coolcatlib$channelAccess()
                .executeOnChannels(channels -> channels.forEach(channel -> ((FilterableChannel) channel).coolcatlib$refilter()));
    }

    // ---------------- named filters (game thread) ----------------

    public static void set(ResourceLocation id, SoundFilter filter, int fadeTicks) {
        Named current = NAMED.get(id);
        SoundFilter from = current == null ? SoundFilter.NONE : current.now();
        NAMED.put(id, new Named(from, filter, Math.max(0, fadeTicks), false));
        update();
    }

    public static void clear(ResourceLocation id, int fadeTicks) {
        Named current = NAMED.get(id);
        if (current == null) return;
        if (fadeTicks <= 0) NAMED.remove(id);
        else NAMED.put(id, new Named(current.now(), SoundFilter.NONE, fadeTicks, true));
        update();
    }

    public static boolean isSet(ResourceLocation id) {
        return NAMED.containsKey(id);
    }

    /** What the named filters come to now. */
    public static SoundFilter named() {
        return named;
    }

    /** Whether OpenAL's EFX was there the last time a sound was filtered (and whether the reverb is). */
    public static boolean efx() {
        return supported;
    }

    public static boolean reverb() {
        return supported && reverbSupported;
    }

    public static void tick(Minecraft minecraft) {
        if (NAMED.isEmpty()) return;
        if (minecraft.level == null) {              // left the world: nothing carries over
            NAMED.clear();
            update();
            return;
        }
        boolean fading = false;
        for (var iterator = NAMED.values().iterator(); iterator.hasNext(); ) {
            Named entry = iterator.next();
            if (entry.age >= entry.ticks) continue;
            entry.age++;
            fading = true;
            if (entry.age >= entry.ticks && entry.clearing) iterator.remove();
        }
        if (fading) update();
    }

    // Works out what the named filters come to, and filters what is playing again if that changed.
    private static void update() {
        SoundFilter combined = SoundFilter.NONE;
        for (Named entry : NAMED.values()) combined = combined.then(entry.now());
        if (combined.equals(named)) return;
        named = combined;
        if (Minecraft.getInstance().getSoundManager() != null) refresh();
    }

    // A named filter on its way from one filter to another over `ticks`.
    private static final class Named {
        private final SoundFilter from;
        private final SoundFilter to;
        private final int ticks;
        private final boolean clearing;
        private int age;

        Named(SoundFilter from, SoundFilter to, int ticks, boolean clearing) {
            this.from = from;
            this.to = to;
            this.ticks = ticks;
            this.clearing = clearing;
        }

        SoundFilter now() {
            return this.age >= this.ticks ? this.to : this.from.lerp(this.to, (float) this.age / this.ticks);
        }
    }

    // ---------------- applying (sound thread) ----------------

    /** Filters the sound on an OpenAL source. On the sound thread, from the channel. */
    public static void apply(int source, SoundContext sound) {
        // The menus' clicks (this version has no UI category: they play as MASTER sounds relative to the listener) are
        // left alone by the named filters.
        boolean menu = sound.relative() && sound.source() == SoundSource.MASTER;
        SoundFilter combined = menu ? SoundFilter.NONE : named;
        for (SoundFilterProvider provider : PROVIDERS) combined = combined.then(provider.filter(sound));
        if (!ready()) return;
        if (combined.isNone()) {
            AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
            AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER, EXTEfx.AL_EFFECTSLOT_NULL, 0, EXTEfx.AL_FILTER_NULL);
            if (reverbSupported) AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER, EXTEfx.AL_EFFECTSLOT_NULL, 1, EXTEfx.AL_FILTER_NULL);
            return;
        }
        EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, combined.gain());
        EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, combined.gainHF());
        AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, filter);
        // What goes to each effect is filtered as the sound is, and only as much of it as asked for.
        send(source, 0, distortionSlot, distortionFilter, combined, combined.distortion());
        if (reverbSupported) send(source, 1, reverbSlot, reverbFilter, combined, combined.reverb());
    }

    private static void send(int source, int send, int slot, int sendFilter, SoundFilter filter, float amount) {
        if (amount <= 0.001F) {
            AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER, EXTEfx.AL_EFFECTSLOT_NULL, send, EXTEfx.AL_FILTER_NULL);
            return;
        }
        EXTEfx.alFilterf(sendFilter, EXTEfx.AL_LOWPASS_GAIN, filter.gain() * amount);
        EXTEfx.alFilterf(sendFilter, EXTEfx.AL_LOWPASS_GAINHF, filter.gainHF());
        AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER, slot, send, sendFilter);
    }

    // Whether the filters are there to use, making them the first time, and again for a new OpenAL context. The reverb
    // needs a second send per source, which OpenAL Soft gives by default; without it, sounds just don't echo.
    private static boolean ready() {
        long context = ALC10.alcGetCurrentContext();
        if (context == 0L) return false;
        if (context == filterContext) return supported;
        filterContext = context;
        long device = ALC10.alcGetContextsDevice(context);
        supported = ALC10.alcIsExtensionPresent(device, "ALC_EXT_EFX");
        if (!supported) return false;
        filter = lowPass();
        distortionFilter = lowPass();
        reverbFilter = lowPass();
        int distortion = EXTEfx.alGenEffects();
        EXTEfx.alEffecti(distortion, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_DISTORTION);
        EXTEfx.alEffectf(distortion, EXTEfx.AL_DISTORTION_EDGE, DISTORTION_EDGE);
        EXTEfx.alEffectf(distortion, EXTEfx.AL_DISTORTION_EQCENTER, DISTORTION_EQ_CENTER);
        EXTEfx.alEffectf(distortion, EXTEfx.AL_DISTORTION_EQBANDWIDTH, DISTORTION_EQ_BANDWIDTH);
        EXTEfx.alEffectf(distortion, EXTEfx.AL_DISTORTION_LOWPASS_CUTOFF, DISTORTION_LOWPASS);
        distortionSlot = EXTEfx.alGenAuxiliaryEffectSlots();
        EXTEfx.alAuxiliaryEffectSloti(distortionSlot, EXTEfx.AL_EFFECTSLOT_EFFECT, distortion);
        reverbSupported = ALC10.alcGetInteger(device, EXTEfx.ALC_MAX_AUXILIARY_SENDS) >= 2;
        if (reverbSupported) {
            int reverb = EXTEfx.alGenEffects();
            EXTEfx.alEffecti(reverb, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_REVERB);
            EXTEfx.alEffectf(reverb, EXTEfx.AL_REVERB_DECAY_TIME, REVERB_DECAY);
            EXTEfx.alEffectf(reverb, EXTEfx.AL_REVERB_DENSITY, REVERB_DENSITY);
            EXTEfx.alEffectf(reverb, EXTEfx.AL_REVERB_DIFFUSION, REVERB_DIFFUSION);
            reverbSlot = EXTEfx.alGenAuxiliaryEffectSlots();
            EXTEfx.alAuxiliaryEffectSloti(reverbSlot, EXTEfx.AL_EFFECTSLOT_EFFECT, reverb);
        }
        supported = AL10.alGetError() == AL10.AL_NO_ERROR;
        return supported;
    }

    private static int lowPass() {
        int made = EXTEfx.alGenFilters();
        EXTEfx.alFilteri(made, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
        return made;
    }
}
