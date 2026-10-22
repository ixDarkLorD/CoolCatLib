package net.ixdarklord.coolcatcanvas.api.armature;

import net.ixdarklord.coolcatcanvas.api.utils.Easing;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * How to play one clip: how it loops, how fast, and how it blends in from whatever was playing. Motions are immutable
 * values, so keep them in constants; each method returns a changed copy. Both sides know them, so the server can send
 * one with {@code ArmatureControl}.
 * <pre>{@code
 * static final Motion WALK = Motion.loop("walk");                                   // the layer's transition rules
 * static final Motion RUN = Motion.loop("run").transition(Transition.crossfade(0.3F));
 * static final Motion ATTACK = Motion.once("attack").speed(1.5F).then(Motion.loop("idle"));
 * static final Motion SPIN = Motion.loop("spin").repeat(3).fade(0.1F).ease(Easing.BACK_OUT);
 * }</pre>
 * A motion that names no transition ({@link #transition}, {@link #fade}, {@link #ease}) blends in the way its layer's
 * rules say, so how animations meet can be set in one place.
 */
public final class Motion {
    public static final float DEFAULT_FADE = Transition.DEFAULT.duration();

    public static final StreamCodec<FriendlyByteBuf, Motion> STREAM_CODEC = StreamCodec.of(Motion::write, Motion::read);

    private final String clip;
    private final @Nullable LoopMode loop;
    private final int repeat;
    private final float speed;
    private final @Nullable Transition transition;
    private final float fadeOut;
    private final float startAt;
    private final boolean restart;
    private final @Nullable Motion next;

    private Motion(String clip, @Nullable LoopMode loop, int repeat, float speed, @Nullable Transition transition, float fadeOut, float startAt, boolean restart, @Nullable Motion next) {
        this.clip = clip;
        this.loop = loop;
        this.repeat = repeat;
        this.speed = speed;
        this.transition = transition;
        this.fadeOut = fadeOut;
        this.startAt = startAt;
        this.restart = restart;
        this.next = next;
    }

    /** A clip, looping the way its file says (Blockbench's loop setting, or Bedrock's {@code loop}). */
    public static Motion of(String clip) {
        return new Motion(clip, null, 0, 1.0F, null, -1.0F, 0.0F, false, null);
    }

    public static Motion loop(String clip) {
        return of(clip).loopMode(LoopMode.LOOP);
    }

    public static Motion once(String clip) {
        return of(clip).loopMode(LoopMode.ONCE);
    }

    public static Motion hold(String clip) {
        return of(clip).loopMode(LoopMode.HOLD);
    }

    public static Motion pingPong(String clip) {
        return of(clip).loopMode(LoopMode.PING_PONG);
    }

    // ---- Changed copies ----

    public Motion loopMode(@Nullable LoopMode loop) {
        return new Motion(this.clip, loop, this.repeat, this.speed, this.transition, this.fadeOut, this.startAt, this.restart, this.next);
    }

    /** Loops this many times, then ends as {@link LoopMode#ONCE} would; 0 loops forever. Makes it a {@link LoopMode#LOOP}. */
    public Motion repeat(int times) {
        return new Motion(this.clip, this.loop == LoopMode.PING_PONG ? LoopMode.PING_PONG : LoopMode.LOOP, Math.max(0, times), this.speed, this.transition, this.fadeOut, this.startAt, this.restart, this.next);
    }

    /** Plays faster (2) or slower (0.5); negative plays backwards. */
    public Motion speed(float speed) {
        return new Motion(this.clip, this.loop, this.repeat, speed, this.transition, this.fadeOut, this.startAt, this.restart, this.next);
    }

    /** How it blends in from what was playing, whatever its layer's rules say; null leaves it to them. */
    public Motion transition(@Nullable Transition transition) {
        return new Motion(this.clip, this.loop, this.repeat, this.speed, transition, this.fadeOut, this.startAt, this.restart, this.next);
    }

    /** Seconds to blend in from what was playing (a crossfade unless {@link #transition} says otherwise). 0 cuts. */
    public Motion fade(float seconds) {
        Transition base = this.transition != null ? this.transition : Transition.DEFAULT;
        return this.transition(seconds <= 0.0F ? Transition.CUT : base.withDuration(seconds));
    }

    /** The curve of the blend in. Only named curves ({@code Easings}) can be sent from the server. */
    public Motion ease(Easing easing) {
        Transition base = this.transition != null ? this.transition : Transition.DEFAULT;
        return this.transition(base.withEasing(easing));
    }

    /** Seconds to blend back to rest when a {@link LoopMode#ONCE} clip ends with nothing after it (else its blend-in time). */
    public Motion fadeOut(float seconds) {
        return new Motion(this.clip, this.loop, this.repeat, this.speed, this.transition, Math.max(0.0F, seconds), this.startAt, this.restart, this.next);
    }

    /** Starts this many seconds into the clip. */
    public Motion startAt(float seconds) {
        return new Motion(this.clip, this.loop, this.repeat, this.speed, this.transition, this.fadeOut, seconds, this.restart, this.next);
    }

    /**
     * Plays from the start even if this clip is already playing on the layer. Without it, asking for the clip that is
     * playing changes nothing, which is what a driver asked every frame needs.
     */
    public Motion restart() {
        return new Motion(this.clip, this.loop, this.repeat, this.speed, this.transition, this.fadeOut, this.startAt, true, this.next);
    }

    /** What plays when this ends (a {@link LoopMode#ONCE} clip, or a loop with a repeat count). Chains can be long. */
    public Motion then(Motion next) {
        Motion chained = this.next == null ? next : this.next.then(next);
        return new Motion(this.clip, this.loop, this.repeat, this.speed, this.transition, this.fadeOut, this.startAt, this.restart, chained);
    }

    // ---- Reading ----

    public String clip() {
        return this.clip;
    }

    /** The loop mode asked for; null means the clip's own. */
    public @Nullable LoopMode loop() {
        return this.loop;
    }

    public int repeat() {
        return this.repeat;
    }

    public float speed() {
        return this.speed;
    }

    /** The transition asked for; null leaves it to the layer. */
    public @Nullable Transition transition() {
        return this.transition;
    }

    /** Seconds it blends in over, as asked; the default if it leaves that to its layer. */
    public float fadeIn() {
        return this.transition != null ? this.transition.duration() : DEFAULT_FADE;
    }

    /** Seconds to blend back to rest at its end, if asked; negative when not (its blend-in time is used). */
    public float fadeOutAsked() {
        return this.fadeOut;
    }

    public float fadeOut() {
        return this.fadeOut >= 0.0F ? this.fadeOut : this.fadeIn();
    }

    public Easing easing() {
        return this.transition != null ? this.transition.easing() : Transition.DEFAULT.easing();
    }

    public float startAt() {
        return this.startAt;
    }

    public boolean restarts() {
        return this.restart;
    }

    public @Nullable Motion next() {
        return this.next;
    }

    /** Whether the two would play the same thing, so switching from one to the other needs no new blend. */
    public boolean sameAs(Motion other) {
        return this.clip.equals(other.clip) && this.loop == other.loop && this.speed == other.speed && this.repeat == other.repeat;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof Motion m && this.sameAs(m) && Objects.equals(this.transition, m.transition) && this.fadeOut == m.fadeOut
                && this.startAt == m.startAt && this.restart == m.restart && Objects.equals(this.next, m.next);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.clip, this.loop, this.speed, this.repeat);
    }

    @Override
    public String toString() {
        return "Motion[" + this.clip + (this.loop != null ? ", " + this.loop : "") + (this.next != null ? " -> " + this.next : "") + "]";
    }

    // ---- Network ----

    private static void write(FriendlyByteBuf buf, Motion motion) {
        buf.writeUtf(motion.clip);
        buf.writeByte(motion.loop == null ? 0 : motion.loop.ordinal() + 1);
        buf.writeVarInt(motion.repeat);
        buf.writeFloat(motion.speed);
        buf.writeBoolean(motion.transition != null);
        if (motion.transition != null) Transition.STREAM_CODEC.encode(buf, motion.transition);
        buf.writeFloat(motion.fadeOut);
        buf.writeFloat(motion.startAt);
        buf.writeBoolean(motion.restart);
        buf.writeBoolean(motion.next != null);
        if (motion.next != null) write(buf, motion.next);
    }

    private static Motion read(FriendlyByteBuf buf) {
        String clip = buf.readUtf();
        int loop = buf.readByte();
        int repeat = buf.readVarInt();
        float speed = buf.readFloat();
        Transition transition = buf.readBoolean() ? Transition.STREAM_CODEC.decode(buf) : null;
        float fadeOut = buf.readFloat();
        float startAt = buf.readFloat();
        boolean restart = buf.readBoolean();
        Motion next = buf.readBoolean() ? read(buf) : null;
        return new Motion(clip, loop <= 0 || loop > LoopMode.values().length ? null : LoopMode.values()[loop - 1], repeat, speed,
                transition, fadeOut, startAt, restart, next);
    }
}
