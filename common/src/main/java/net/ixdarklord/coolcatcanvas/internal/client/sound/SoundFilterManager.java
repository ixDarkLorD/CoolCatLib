package net.ixdarklord.coolcatcanvas.internal.client.sound;

import net.ixdarklord.coolcatcanvas.api.client.sound.SoundFilter;
import net.ixdarklord.coolcatcanvas.api.client.sound.SoundFilterProvider;
import net.ixdarklord.coolcatcanvas.internal.mixin.client.SoundEngineAccessor;
import net.ixdarklord.coolcatcanvas.internal.mixin.client.SoundManagerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The sound filters' work ({@code api.client.sound.SoundFilters}): the providers' filters combined for each sound, as
 * an EFX low-pass filter on its OpenAL source, and as much of it as they say sent, muffled the same, through a
 * distortion effect. Applied by {@code ChannelMixin} when a sound starts and as it moves.
 */
public final class SoundFilterManager {
    private static final List<SoundFilterProvider> PROVIDERS = new CopyOnWriteArrayList<>();
    // The distortion: the edge it gives (0 to 1), the band it works on, and the highs it keeps.
    private static final float DISTORTION_EDGE = 0.3F;
    private static final float DISTORTION_EQ_CENTER = 700.0F;
    private static final float DISTORTION_EQ_BANDWIDTH = 2000.0F;
    private static final float DISTORTION_LOWPASS = 3000.0F;
    // The filter objects (one for the sound itself, one for what goes to the distortion), set for each sound before they
    // are attached (a source keeps a copy of a filter's settings); the distortion and the slot it plays in; and the
    // OpenAL context they belong to: a new one (the audio device changed) needs them made again. Sound thread only.
    private static int filter;
    private static int sendFilter;
    private static int distortionSlot;
    private static long filterContext;
    private static boolean supported;

    private SoundFilterManager() {}

    public static void register(SoundFilterProvider provider) {
        PROVIDERS.add(provider);
    }

    public static void refresh() {
        SoundManagerAccessor manager = (SoundManagerAccessor) Minecraft.getInstance().getSoundManager();
        ((SoundEngineAccessor) manager.coolcatlib$soundEngine()).coolcatlib$channelAccess()
                .executeOnChannels(channels -> channels.forEach(channel -> ((FilterableChannel) channel).coolcatlib$refilter()));
    }

    /**
     * Filters the sound on an OpenAL source, playing at this place, or relative to the listener (never filtered). On the
     * sound thread, from the channel.
     */
    public static void apply(int source, Vec3 position, boolean relative) {
        SoundFilter combined = SoundFilter.NONE;
        if (!relative) {
            for (SoundFilterProvider provider : PROVIDERS) combined = combined.then(provider.filter(position));
        }
        if (!ready()) return;
        if (combined.isNone()) {
            AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
            AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER, EXTEfx.AL_EFFECTSLOT_NULL, 0, EXTEfx.AL_FILTER_NULL);
            return;
        }
        EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, combined.gain());
        EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, combined.gainHF());
        AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, filter);
        if (combined.distortion() <= 0.001F) {
            AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER, EXTEfx.AL_EFFECTSLOT_NULL, 0, EXTEfx.AL_FILTER_NULL);
            return;
        }
        // What goes to the distortion is muffled as the sound is, and only as much of it as asked for.
        EXTEfx.alFilterf(sendFilter, EXTEfx.AL_LOWPASS_GAIN, combined.gain() * combined.distortion());
        EXTEfx.alFilterf(sendFilter, EXTEfx.AL_LOWPASS_GAINHF, combined.gainHF());
        AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER, distortionSlot, 0, sendFilter);
    }

    // Whether the filters are there to use, making them the first time, and again for a new OpenAL context.
    private static boolean ready() {
        long context = ALC10.alcGetCurrentContext();
        if (context == 0L) return false;
        if (context == filterContext) return supported;
        filterContext = context;
        supported = ALC10.alcIsExtensionPresent(ALC10.alcGetContextsDevice(context), "ALC_EXT_EFX");
        if (!supported) return false;
        filter = EXTEfx.alGenFilters();
        EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
        sendFilter = EXTEfx.alGenFilters();
        EXTEfx.alFilteri(sendFilter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
        int distortion = EXTEfx.alGenEffects();
        EXTEfx.alEffecti(distortion, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_DISTORTION);
        EXTEfx.alEffectf(distortion, EXTEfx.AL_DISTORTION_EDGE, DISTORTION_EDGE);
        EXTEfx.alEffectf(distortion, EXTEfx.AL_DISTORTION_EQCENTER, DISTORTION_EQ_CENTER);
        EXTEfx.alEffectf(distortion, EXTEfx.AL_DISTORTION_EQBANDWIDTH, DISTORTION_EQ_BANDWIDTH);
        EXTEfx.alEffectf(distortion, EXTEfx.AL_DISTORTION_LOWPASS_CUTOFF, DISTORTION_LOWPASS);
        distortionSlot = EXTEfx.alGenAuxiliaryEffectSlots();
        EXTEfx.alAuxiliaryEffectSloti(distortionSlot, EXTEfx.AL_EFFECTSLOT_EFFECT, distortion);
        supported = AL10.alGetError() == AL10.AL_NO_ERROR;
        return supported;
    }
}
