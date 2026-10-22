package net.ixdarklord.coolcatcanvas.api.armature;

import net.ixdarklord.coolcatcanvas.api.utils.Easing;
import net.ixdarklord.coolcatcanvas.api.utils.Easings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * How one animation gives way to the next: over how long, along which curve, and how the two meet.
 * <pre>{@code
 * Transition.crossfade(0.3F)                        // both move while they blend: walk into run
 * Transition.freeze(0.25F, Easing.CUBIC_OUT)        // the old pose holds still while the new one blends in
 * Transition.CUT                                    // no blend at all
 * }</pre>
 * A motion can carry its own ({@link Motion#transition}); otherwise its layer decides, by its rules or its default
 * (see {@code AnimLayer.transition}). Immutable.
 *
 * @param duration seconds the blend takes; 0 cuts
 * @param easing   how the blend is paced (only named curves, {@link Easings}, can be sent from the server)
 */
public record Transition(float duration, Easing easing, Mode mode) {
    /** What a layer uses when nothing else is set: a fifth of a second, crossfading, sine in-out. */
    public static final Transition DEFAULT = new Transition(0.2F, Easing.SINE_IN_OUT, Mode.CROSSFADE);
    /** Straight to the next animation. */
    public static final Transition CUT = new Transition(0.0F, Easing.LINEAR, Mode.CUT);

    public static final StreamCodec<FriendlyByteBuf, Transition> STREAM_CODEC = StreamCodec.of(
            (buf, transition) -> {
                buf.writeFloat(transition.duration);
                String easing = Easings.nameOf(transition.easing);
                buf.writeUtf(easing == null ? "sine_in_out" : easing);
                buf.writeEnum(transition.mode);
            },
            buf -> {
                float duration = buf.readFloat();
                Easing easing = Easings.byName(buf.readUtf());
                return new Transition(duration, easing == null ? Easing.SINE_IN_OUT : easing, buf.readEnum(Mode.class));
            });

    public Transition {
        duration = Math.max(0.0F, duration);
        if (mode == Mode.CUT) duration = 0.0F;
    }

    /** Both animations keep moving while one blends into the other. */
    public static Transition crossfade(float seconds) {
        return new Transition(seconds, Easing.SINE_IN_OUT, Mode.CROSSFADE);
    }

    public static Transition crossfade(float seconds, Easing easing) {
        return new Transition(seconds, easing, Mode.CROSSFADE);
    }

    /**
     * The old animation stops where it is and the new one blends in from that still pose: seamless even when the old
     * one is cut off mid-swing, and never a mix of two motions at once.
     */
    public static Transition freeze(float seconds) {
        return new Transition(seconds, Easing.SINE_IN_OUT, Mode.FREEZE);
    }

    public static Transition freeze(float seconds, Easing easing) {
        return new Transition(seconds, easing, Mode.FREEZE);
    }

    public Transition withDuration(float seconds) {
        return new Transition(seconds, this.easing, this.mode == Mode.CUT ? Mode.CROSSFADE : this.mode);
    }

    public Transition withEasing(Easing easing) {
        return new Transition(this.duration, easing, this.mode);
    }

    /** Whether there is a blend at all. */
    public boolean blends() {
        return this.mode != Mode.CUT && this.duration > 0.0F;
    }

    public enum Mode {
        /** Both animations keep playing while they blend. */
        CROSSFADE,
        /** The old animation holds its last pose while the new one blends in. */
        FREEZE,
        /** No blend. */
        CUT
    }
}
