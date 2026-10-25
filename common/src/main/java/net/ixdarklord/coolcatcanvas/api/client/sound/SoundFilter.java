package net.ixdarklord.coolcatcanvas.api.client.sound;

import net.minecraft.util.Mth;

/**
 * How a sound comes through to the listener: how loud it stays ({@code gain}), how much of its high end it keeps
 * ({@code gainHF}), and how much of it is sent through a distortion ({@code distortion}), each 0 to 1. Losing the highs
 * is what makes a sound muffled, as through a wall; a little distortion makes it rough, as if strained through
 * something.
 * <pre>{@code
 * new SoundFilter(0.8F, 0.2F)           // through a wall: a little quieter, most of the highs gone
 * new SoundFilter(0.6F, 0.1F, 0.3F)     // muffled and rough, as under water
 * }</pre>
 *
 * @param gain       how loud the sound stays, 1 for as loud as it was
 * @param gainHF     how much of its high end it keeps, 1 for all of it
 * @param distortion how much of it is sent through the distortion, 0 for none
 */
public record SoundFilter(float gain, float gainHF, float distortion) {
    /** The sound as it is. */
    public static final SoundFilter NONE = new SoundFilter(1.0F, 1.0F, 0.0F);

    public SoundFilter {
        gain = Mth.clamp(gain, 0.0F, 1.0F);
        gainHF = Mth.clamp(gainHF, 0.0F, 1.0F);
        distortion = Mth.clamp(distortion, 0.0F, 1.0F);
    }

    /** A filter that only muffles: no distortion. */
    public SoundFilter(float gain, float gainHF) {
        this(gain, gainHF, 0.0F);
    }

    /** Both filters at once, as a sound through one wall and then another. */
    public SoundFilter then(SoundFilter other) {
        return new SoundFilter(this.gain * other.gain, this.gainHF * other.gainHF, 1.0F - (1.0F - this.distortion) * (1.0F - other.distortion));
    }

    /** Whether it changes nothing. */
    public boolean isNone() {
        return this.gain >= 0.999F && this.gainHF >= 0.999F && this.distortion <= 0.001F;
    }
}
