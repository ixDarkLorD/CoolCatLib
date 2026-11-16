package net.ixdarklord.coolcatcanvas.api.client.sound;

import net.minecraft.util.Mth;

/**
 * How a sound comes through to the listener, each part 0 to 1:
 * <ul>
 *   <li>{@code gain}: how loud it stays, 1 for as loud as it was;</li>
 *   <li>{@code gainHF}: how much of its high end it keeps; losing the highs is what makes a sound muffled, as through a
 *   wall;</li>
 *   <li>{@code distortion}: how much of it is sent through a distortion, which makes it rough, as if strained through
 *   something;</li>
 *   <li>{@code reverb}: how much of it is sent into a reverb, which makes it echo, as in a cave or a great hall.</li>
 * </ul>
 * <pre>{@code
 * SoundFilter.muffled(0.8F)                          // through a thick wall
 * new SoundFilter(0.6F, 0.1F, 0.3F)                  // muffled and rough, as under water
 * SoundFilter.echoing(0.5F).then(SoundFilter.quieter(0.8F))
 * SoundFilter.NONE.lerp(underwater, fade)            // part of the way there, for a fade
 * }</pre>
 *
 * @param gain       how loud the sound stays
 * @param gainHF     how much of its high end it keeps
 * @param distortion how much of it is sent through the distortion
 * @param reverb     how much of it is sent into the reverb
 */
public record SoundFilter(float gain, float gainHF, float distortion, float reverb) {
    /** The sound as it is. */
    public static final SoundFilter NONE = new SoundFilter(1.0F, 1.0F, 0.0F, 0.0F);

    public SoundFilter {
        gain = Mth.clamp(gain, 0.0F, 1.0F);
        gainHF = Mth.clamp(gainHF, 0.0F, 1.0F);
        distortion = Mth.clamp(distortion, 0.0F, 1.0F);
        reverb = Mth.clamp(reverb, 0.0F, 1.0F);
    }

    /** A filter without reverb. */
    public SoundFilter(float gain, float gainHF, float distortion) {
        this(gain, gainHF, distortion, 0.0F);
    }

    /** A filter that only muffles. */
    public SoundFilter(float gain, float gainHF) {
        this(gain, gainHF, 0.0F, 0.0F);
    }

    /** Muffled, as through a wall: 0 for not at all, 1 for nearly nothing of the highs left and a little quieter. */
    public static SoundFilter muffled(float amount) {
        amount = Mth.clamp(amount, 0.0F, 1.0F);
        return new SoundFilter(1.0F - 0.25F * amount, 1.0F - 0.95F * amount);
    }

    /** Only quieter: {@code gain} of the volume left. */
    public static SoundFilter quieter(float gain) {
        return new SoundFilter(gain, 1.0F);
    }

    /** Only rough: {@code amount} of it sent through the distortion. */
    public static SoundFilter distorted(float amount) {
        return new SoundFilter(1.0F, 1.0F, amount, 0.0F);
    }

    /** Only echoing: {@code amount} of it sent into the reverb. */
    public static SoundFilter echoing(float amount) {
        return new SoundFilter(1.0F, 1.0F, 0.0F, amount);
    }

    /** This filter with another distortion. */
    public SoundFilter withDistortion(float distortion) {
        return new SoundFilter(this.gain, this.gainHF, distortion, this.reverb);
    }

    /** This filter with another reverb. */
    public SoundFilter withReverb(float reverb) {
        return new SoundFilter(this.gain, this.gainHF, this.distortion, reverb);
    }

    /** Both filters at once, as a sound through one wall and then another. */
    public SoundFilter then(SoundFilter other) {
        return new SoundFilter(this.gain * other.gain, this.gainHF * other.gainHF,
                1.0F - (1.0F - this.distortion) * (1.0F - other.distortion), 1.0F - (1.0F - this.reverb) * (1.0F - other.reverb));
    }

    /** Part of the way from this filter to another: 0 is this one, 1 the other. For fades. */
    public SoundFilter lerp(SoundFilter to, float delta) {
        delta = Mth.clamp(delta, 0.0F, 1.0F);
        return new SoundFilter(Mth.lerp(delta, this.gain, to.gain), Mth.lerp(delta, this.gainHF, to.gainHF),
                Mth.lerp(delta, this.distortion, to.distortion), Mth.lerp(delta, this.reverb, to.reverb));
    }

    /** Whether it changes nothing. */
    public boolean isNone() {
        return this.gain >= 0.999F && this.gainHF >= 0.999F && this.distortion <= 0.001F && this.reverb <= 0.001F;
    }
}
