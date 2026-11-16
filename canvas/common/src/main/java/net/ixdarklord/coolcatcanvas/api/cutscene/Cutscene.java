package net.ixdarklord.coolcatcanvas.api.cutscene;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.coolcatcanvas.api.utils.Easing;
import net.ixdarklord.coolcatcanvas.api.utils.Easings;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A cutscene: a camera flown along keyframes while the player watches, with letterbox bars, fades, subtitles and
 * markers in time with it. It is plain data, the same on both sides: built in code, read from
 * {@code assets/<namespace>/cutscene/<path>.json}, made in the in-game editor, or sent by the server.
 * <pre>{@code
 * Cutscene intro = Cutscene.builder()
 *         .keyframe(0, new Vec3(10, 80, 10), 135, 20)
 *         .keyframe(100, new Vec3(-4, 72, 30), 200, 5).easing(Easing.SINE_IN_OUT).fov(45)
 *         .keyframe(160, new Vec3(-6, 70, 42), 180, 0)
 *         .letterbox()
 *         .fadeIn(15).fadeOut(15)
 *         .subtitle(20, 70, Component.literal("Long ago..."))
 *         .marker(100, MyMod.id("thunder"))
 *         .build();
 * }</pre>
 * Times are ticks from the start; fractions are allowed. On the client, {@code Cutscenes} registers and plays them;
 * on the server, {@link CutsceneControl} plays them for a player.
 * <p>
 * The camera only shows what the client has loaded: chunks around the player, not around the camera. Keep a path
 * within the player's view distance of where they stand.
 */
public final class Cutscene {
    /** The widescreen cinema ratio the letterbox bars frame by default. */
    public static final float CINEMATIC_ASPECT = 2.39F;
    private static final float DEFAULT_BARS_TICKS = 15.0F;

    public static final Codec<Cutscene> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CameraKeyframe.CODEC.listOf().fieldOf("keyframes").forGetter(Cutscene::keyframes),
            Codec.FLOAT.optionalFieldOf("duration").forGetter(cutscene -> Optional.of(cutscene.duration)),
            CutsceneAnchor.CODEC.optionalFieldOf("anchor", CutsceneAnchor.WORLD).forGetter(Cutscene::anchor),
            Codec.FLOAT.optionalFieldOf("letterbox", 0.0F).forGetter(Cutscene::letterbox),
            ValueTrack.CODEC.optionalFieldOf("bars", ValueTrack.EMPTY).forGetter(cutscene -> cutscene.bars),
            ValueTrack.CODEC.optionalFieldOf("fade", ValueTrack.EMPTY).forGetter(Cutscene::fade),
            ExtraCodecs.STRING_RGB_COLOR.optionalFieldOf("fade_color", 0xFF000000).forGetter(cutscene -> cutscene.fadeColor | 0xFF000000),
            CameraFocus.CODEC.listOf().optionalFieldOf("focus", List.of()).forGetter(Cutscene::focuses),
            Subtitle.CODEC.listOf().optionalFieldOf("subtitles", List.of()).forGetter(Cutscene::subtitles),
            Marker.CODEC.listOf().optionalFieldOf("markers", List.of()).forGetter(Cutscene::markers),
            Codec.FLOAT.optionalFieldOf("blend_in", 0.0F).forGetter(Cutscene::blendIn),
            Codec.FLOAT.optionalFieldOf("blend_out", 0.0F).forGetter(Cutscene::blendOut),
            Codec.BOOL.optionalFieldOf("hide_hud", true).forGetter(Cutscene::hideHud),
            Codec.BOOL.optionalFieldOf("lock_input", true).forGetter(Cutscene::lockInput),
            Codec.BOOL.optionalFieldOf("skippable", true).forGetter(Cutscene::skippable),
            Codec.BOOL.optionalFieldOf("editable", true).forGetter(Cutscene::editable)
    ).apply(instance, Cutscene::new));

    /**
     * A line of text shown at the foot of the screen for a while.
     *
     * @param start    when it appears, in ticks from the cutscene's start
     * @param duration how long it stays
     */
    public record Subtitle(float start, float duration, Component text) {
        public static final Codec<Subtitle> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.fieldOf("start").forGetter(Subtitle::start),
                Codec.FLOAT.fieldOf("duration").forGetter(Subtitle::duration),
                ComponentSerialization.CODEC.fieldOf("text").forGetter(Subtitle::text)
        ).apply(instance, Subtitle::new));

        public float end() {
            return this.start + this.duration;
        }
    }

    /**
     * A named moment: when the cutscene passes it, the client fires {@code CutsceneEvents.MARKER} with its id, for a
     * sound, an effect, a camera shake, anything that should land on a beat.
     */
    public record Marker(float time, Identifier id) {
        public static final Codec<Marker> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.fieldOf("time").forGetter(Marker::time),
                Identifier.CODEC.fieldOf("id").forGetter(Marker::id)
        ).apply(instance, Marker::new));
    }

    private final List<CameraKeyframe> keyframes;
    private final float duration;
    private final CutsceneAnchor anchor;
    private final float letterbox;
    private final ValueTrack bars;
    private final ValueTrack fade;
    private final int fadeColor;
    private final List<CameraFocus> focuses;
    private final List<Subtitle> subtitles;
    private final List<Marker> markers;
    private final float blendIn;
    private final float blendOut;
    private final boolean hideHud;
    private final boolean lockInput;
    private final boolean skippable;
    private final boolean editable;
    // Each keyframe's yaw and roll counted on from the one before by the short way round, so they can be curved through.
    private final float[] yaws;
    private final float[] rolls;

    private Cutscene(List<CameraKeyframe> keyframes, Optional<Float> duration, CutsceneAnchor anchor, float letterbox, ValueTrack bars, ValueTrack fade,
                     int fadeColor, List<CameraFocus> focuses, List<Subtitle> subtitles, List<Marker> markers, float blendIn, float blendOut,
                     boolean hideHud, boolean lockInput, boolean skippable, boolean editable) {
        if (keyframes.isEmpty()) throw new IllegalArgumentException("A cutscene needs at least one keyframe");
        List<CameraKeyframe> sorted = new ArrayList<>(keyframes);
        sorted.sort(Comparator.comparingDouble(CameraKeyframe::time));
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).time() <= sorted.get(i - 1).time()) throw new IllegalArgumentException("Two keyframes share the time " + sorted.get(i).time());
        }
        this.keyframes = List.copyOf(sorted);
        this.duration = duration.orElse(sorted.getLast().time());
        if (!(this.duration > 0.0F)) throw new IllegalArgumentException("A cutscene with one keyframe needs a duration");
        this.anchor = anchor;
        if (letterbox < 0.0F) throw new IllegalArgumentException("Letterbox aspect can't be negative: " + letterbox);
        this.letterbox = letterbox;
        this.bars = bars;
        this.fade = fade;
        this.fadeColor = fadeColor & 0xFFFFFF;
        this.focuses = List.copyOf(focuses);
        this.subtitles = List.copyOf(subtitles);
        List<Marker> ordered = new ArrayList<>(markers);
        ordered.sort(Comparator.comparingDouble(Marker::time));
        this.markers = List.copyOf(ordered);
        this.blendIn = Mth.clamp(blendIn, 0.0F, this.duration);
        this.blendOut = Mth.clamp(blendOut, 0.0F, this.duration - this.blendIn);
        this.hideHud = hideHud;
        this.lockInput = lockInput;
        this.skippable = skippable;
        this.editable = editable;

        int count = this.keyframes.size();
        this.yaws = new float[count];
        this.rolls = new float[count];
        this.yaws[0] = this.keyframes.getFirst().yaw();
        this.rolls[0] = this.keyframes.getFirst().roll();
        for (int i = 1; i < count; i++) {
            this.yaws[i] = this.yaws[i - 1] + Mth.wrapDegrees(this.keyframes.get(i).yaw() - this.keyframes.get(i - 1).yaw());
            this.rolls[i] = this.rolls[i - 1] + Mth.wrapDegrees(this.keyframes.get(i).roll() - this.keyframes.get(i - 1).roll());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /** A builder holding everything this cutscene has, to make a changed copy. */
    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.keyframes.addAll(this.keyframes);
        builder.duration = this.duration;
        builder.anchor = this.anchor;
        builder.letterbox = this.letterbox;
        builder.bars = this.bars;
        builder.fade = this.fade;
        builder.fadeColor = this.fadeColor;
        builder.focuses.addAll(this.focuses);
        builder.subtitles.addAll(this.subtitles);
        builder.markers.addAll(this.markers);
        builder.blendIn = this.blendIn;
        builder.blendOut = this.blendOut;
        builder.hideHud = this.hideHud;
        builder.lockInput = this.lockInput;
        builder.skippable = this.skippable;
        builder.editable = this.editable;
        return builder;
    }

    // ---- What it holds ----

    /** The keyframes, in time order. */
    public List<CameraKeyframe> keyframes() {
        return this.keyframes;
    }

    /** How long it lasts, in ticks. */
    public float duration() {
        return this.duration;
    }

    public CutsceneAnchor anchor() {
        return this.anchor;
    }

    /** The aspect ratio (width over height) the letterbox bars frame, or 0 for no bars. */
    public float letterbox() {
        return this.letterbox;
    }

    /** How dark the screen is over time, 0 to 1; empty for no fades. */
    public ValueTrack fade() {
        return this.fade;
    }

    /** The colour faded to, as {@code 0xRRGGBB}. */
    public int fadeColor() {
        return this.fadeColor;
    }

    public List<CameraFocus> focuses() {
        return this.focuses;
    }

    public List<Subtitle> subtitles() {
        return this.subtitles;
    }

    /** The markers, in time order. */
    public List<Marker> markers() {
        return this.markers;
    }

    /** Over how many ticks the view glides from the player's own into the first keyframe; 0 cuts. */
    public float blendIn() {
        return this.blendIn;
    }

    /** Over how many ticks the view glides back to the player's own at the end; 0 cuts. */
    public float blendOut() {
        return this.blendOut;
    }

    public boolean hideHud() {
        return this.hideHud;
    }

    /** Whether the player can't move, look or act while it plays. */
    public boolean lockInput() {
        return this.lockInput;
    }

    public boolean skippable() {
        return this.skippable;
    }

    /**
     * Whether the in-game editor may open it. A cutscene that isn't editable is left out of the editor's list, so
     * players can't load a copy of it to change or reuse. It plays the same either way.
     */
    public boolean editable() {
        return this.editable;
    }

    /**
     * Whether it can be written to a file or sent to a client: every easing it uses has a name in {@link Easings}.
     * Cutscenes built with lambdas or {@code reversed()} curves only play where they were built.
     */
    public boolean isPortable() {
        for (CameraKeyframe keyframe : this.keyframes) if (Easings.nameOf(keyframe.easing()) == null) return false;
        for (ValueTrack.Key key : this.bars.keys()) if (Easings.nameOf(key.easing()) == null) return false;
        for (ValueTrack.Key key : this.fade.keys()) if (Easings.nameOf(key.easing()) == null) return false;
        return true;
    }

    // ---- What it looks like at a time ----

    /**
     * How far the letterbox bars are in at a time, 0 to 1. Without a track of its own, a letterboxed cutscene slides
     * its bars in over its first ticks and out over its last.
     */
    public float barsAt(float time) {
        if (this.letterbox <= 0.0F) return 0.0F;
        if (!this.bars.isEmpty()) return Mth.clamp(this.bars.at(time), 0.0F, 1.0F);
        float slide = Math.min(DEFAULT_BARS_TICKS, this.duration / 4.0F);
        float in = Mth.clamp(time / slide, 0.0F, 1.0F);
        float out = Mth.clamp((this.duration - time) / slide, 0.0F, 1.0F);
        return Easing.SINE_IN_OUT.apply(Math.min(in, out));
    }

    /** How dark the fade is at a time, 0 to 1. */
    public float fadeAt(float time) {
        return Mth.clamp(this.fade.at(time), 0.0F, 1.0F);
    }

    /** The camera at a time, in the cutscene's own space, taking 70 for a keyframe without a field of view. */
    public CameraPose poseAt(float time) {
        return this.poseAt(time, 70.0F);
    }

    /**
     * The camera at a time, in the cutscene's own space, before any {@link CameraFocus} turns it.
     *
     * @param playerFov the field of view keyframes without one of their own take
     */
    public CameraPose poseAt(float time, float playerFov) {
        List<CameraKeyframe> keys = this.keyframes;
        int last = keys.size() - 1;
        if (time <= keys.getFirst().time()) return this.poseOf(0, playerFov);
        if (time >= keys.get(last).time()) return this.poseOf(last, playerFov);

        // The keyframe being moved into: the first one after this time.
        int low = 1;
        int high = last;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (keys.get(middle).time() <= time) low = middle + 1;
            else high = middle;
        }
        int to = low;
        int from = to - 1;
        CameraKeyframe a = keys.get(from);
        CameraKeyframe b = keys.get(to);
        if (b.interpolation() == Interpolation.CUT) return this.poseOf(from, playerFov);

        float span = b.time() - a.time();
        float u = b.easing().apply((time - a.time()) / span);
        if (b.interpolation() == Interpolation.LINEAR) {
            return new CameraPose(a.position().lerp(b.position(), u), Mth.lerp(u, this.yaws[from], this.yaws[to]), Mth.lerp(u, a.pitch(), b.pitch()),
                    Mth.lerp(u, this.rolls[from], this.rolls[to]), Mth.lerp(u, fov(a, playerFov), fov(b, playerFov)));
        }

        // A cubic through both keyframes, leaving and arriving at the speed the moves on either side have there.
        float h00 = (2.0F * u - 3.0F) * u * u + 1.0F;
        float h10 = ((u - 2.0F) * u + 1.0F) * u;
        float h01 = (3.0F - 2.0F * u) * u * u;
        float h11 = (u - 1.0F) * u * u;
        int before = from - 1;
        int after = to + 1;
        Interpolation into = from > 0 ? a.interpolation() : Interpolation.CUT;
        Interpolation onward = to < last ? keys.get(after).interpolation() : Interpolation.CUT;
        float spanBefore = from > 0 ? a.time() - keys.get(before).time() : 0.0F;
        float spanAfter = to < last ? keys.get(after).time() - b.time() : 0.0F;

        Vec3 p0 = from > 0 ? keys.get(before).position() : a.position();
        Vec3 p3 = to < last ? keys.get(after).position() : b.position();
        double x = hermite(h00, h10, h01, h11, p0.x, a.position().x, b.position().x, p3.x, into, onward, spanBefore, span, spanAfter);
        double y = hermite(h00, h10, h01, h11, p0.y, a.position().y, b.position().y, p3.y, into, onward, spanBefore, span, spanAfter);
        double z = hermite(h00, h10, h01, h11, p0.z, a.position().z, b.position().z, p3.z, into, onward, spanBefore, span, spanAfter);
        float yaw = (float) hermite(h00, h10, h01, h11, from > 0 ? this.yaws[before] : 0.0, this.yaws[from], this.yaws[to], to < last ? this.yaws[after] : 0.0,
                into, onward, spanBefore, span, spanAfter);
        float pitch = (float) hermite(h00, h10, h01, h11, from > 0 ? keys.get(before).pitch() : 0.0, a.pitch(), b.pitch(), to < last ? keys.get(after).pitch() : 0.0,
                into, onward, spanBefore, span, spanAfter);
        float roll = (float) hermite(h00, h10, h01, h11, from > 0 ? this.rolls[before] : 0.0, this.rolls[from], this.rolls[to], to < last ? this.rolls[after] : 0.0,
                into, onward, spanBefore, span, spanAfter);
        float fov = (float) hermite(h00, h10, h01, h11, from > 0 ? fov(keys.get(before), playerFov) : 0.0, fov(a, playerFov), fov(b, playerFov),
                to < last ? fov(keys.get(after), playerFov) : 0.0, into, onward, spanBefore, span, spanAfter);
        return new CameraPose(new Vec3(x, y, z), yaw, Mth.clamp(pitch, -90.0F, 90.0F), roll, Mth.clamp(fov, 1.0F, 170.0F));
    }

    private CameraPose poseOf(int index, float playerFov) {
        CameraKeyframe key = this.keyframes.get(index);
        return new CameraPose(key.position(), this.yaws[index], key.pitch(), this.rolls[index], fov(key, playerFov));
    }

    private static float fov(CameraKeyframe key, float playerFov) {
        return key.fov() > 0.0F ? key.fov() : playerFov;
    }

    // One value between v1 and v2. The speed at each end, per this move's length: the neighbouring move's own when
    // that one is a straight line, the average across the keyframe when it curves, and this move's when there is no
    // neighbour or it is a cut.
    private static double hermite(float h00, float h10, float h01, float h11, double v0, double v1, double v2, double v3,
                                  Interpolation into, Interpolation onward, float spanBefore, float span, float spanAfter) {
        double chord = v2 - v1;
        double m1 = switch (into) {
            case LINEAR -> (v1 - v0) / spanBefore * span;
            case SMOOTH -> (v2 - v0) / (spanBefore + span) * span;
            case CUT -> chord;
        };
        double m2 = switch (onward) {
            case LINEAR -> (v3 - v2) / spanAfter * span;
            case SMOOTH -> (v3 - v1) / (span + spanAfter) * span;
            case CUT -> chord;
        };
        return h00 * v1 + h10 * m1 + h01 * v2 + h11 * m2;
    }

    /** Builds a {@link Cutscene}. Calls that shape a keyframe act on the one added last. */
    public static final class Builder {
        private final List<CameraKeyframe> keyframes = new ArrayList<>();
        private float duration = -1.0F;
        private CutsceneAnchor anchor = CutsceneAnchor.WORLD;
        private float letterbox;
        private ValueTrack bars = ValueTrack.EMPTY;
        private ValueTrack fade = ValueTrack.EMPTY;
        private float fadeIn;
        private float fadeOut;
        private int fadeColor;
        private final List<CameraFocus> focuses = new ArrayList<>();
        private final List<Subtitle> subtitles = new ArrayList<>();
        private final List<Marker> markers = new ArrayList<>();
        private float blendIn;
        private float blendOut;
        private boolean hideHud = true;
        private boolean lockInput = true;
        private boolean skippable = true;
        private boolean editable = true;

        private Builder() {}

        // ---- Keyframes ----

        /** A keyframe reached along a smooth curve at an even pace; shape it with the calls below. */
        public Builder keyframe(float time, Vec3 position, float yaw, float pitch) {
            return this.keyframe(new CameraKeyframe(time, position, yaw, pitch));
        }

        public Builder keyframe(CameraKeyframe keyframe) {
            this.keyframes.add(keyframe);
            return this;
        }

        /** Replaces every keyframe. */
        public Builder keyframes(List<CameraKeyframe> keyframes) {
            this.keyframes.clear();
            this.keyframes.addAll(keyframes);
            return this;
        }

        /** How the move into the last keyframe is paced. */
        public Builder easing(Easing easing) {
            return this.reshape(this.last().withEasing(easing));
        }

        /** The shape of the move into the last keyframe. */
        public Builder interpolation(Interpolation interpolation) {
            return this.reshape(this.last().withInterpolation(interpolation));
        }

        /** Cuts to the last keyframe instead of moving into it. */
        public Builder cut() {
            return this.interpolation(Interpolation.CUT);
        }

        /** The last keyframe's tilt around the line of sight, in degrees. */
        public Builder roll(float degrees) {
            return this.reshape(this.last().withRoll(degrees));
        }

        /** The last keyframe's field of view, in degrees: narrow to zoom in. */
        public Builder fov(float degrees) {
            return this.reshape(this.last().withFov(degrees));
        }

        private CameraKeyframe last() {
            if (this.keyframes.isEmpty()) throw new IllegalStateException("Add a keyframe first");
            return this.keyframes.getLast();
        }

        private Builder reshape(CameraKeyframe keyframe) {
            this.keyframes.set(this.keyframes.size() - 1, keyframe);
            return this;
        }

        // ---- The cutscene as a whole ----

        /** How long it lasts, in ticks; the last keyframe's time unless set. Longer holds the last keyframe. */
        public Builder duration(float ticks) {
            this.duration = ticks;
            return this;
        }

        public Builder anchor(CutsceneAnchor anchor) {
            this.anchor = anchor;
            return this;
        }

        /** Letterbox bars framing the cinema ratio ({@link #CINEMATIC_ASPECT}), sliding in at the start and out at the end. */
        public Builder letterbox() {
            return this.letterbox(CINEMATIC_ASPECT);
        }

        /**
         * Letterbox bars framing an aspect ratio (width over height): top and bottom on a taller screen, left and
         * right on a wider one. 0 for none.
         */
        public Builder letterbox(float aspect) {
            this.letterbox = aspect;
            return this;
        }

        /** How far the bars are in over time, 0 to 1, in place of sliding in at the start and out at the end. */
        public Builder bars(ValueTrack bars) {
            this.bars = bars;
            return this;
        }

        /** Starts on a covered screen that clears over these ticks. */
        public Builder fadeIn(float ticks) {
            this.fadeIn = ticks;
            return this;
        }

        /** Ends on a covered screen, covering it over the last of these ticks. */
        public Builder fadeOut(float ticks) {
            this.fadeOut = ticks;
            return this;
        }

        /** How dark the screen is over time, 0 to 1, for fades in the middle; replaces {@link #fadeIn} and {@link #fadeOut}. */
        public Builder fade(ValueTrack fade) {
            this.fade = fade;
            return this;
        }

        /** The colour faded to, as {@code 0xRRGGBB}; black unless set. */
        public Builder fadeColor(int rgb) {
            this.fadeColor = rgb;
            return this;
        }

        public Builder focus(CameraFocus focus) {
            this.focuses.add(focus);
            return this;
        }

        /** Keeps the camera looking at a point, in the cutscene's own space, for the whole cutscene. */
        public Builder lookAt(Vec3 point) {
            return this.focus(CameraFocus.onPoint(0.0F, CameraFocus.UNTIL_END, point));
        }

        public Builder subtitle(float start, float duration, Component text) {
            this.subtitles.add(new Subtitle(start, duration, text));
            return this;
        }

        public Builder marker(float time, Identifier id) {
            this.markers.add(new Marker(time, id));
            return this;
        }

        /** Glides from the player's own view into the first keyframe over these ticks, instead of cutting to it. */
        public Builder blendIn(float ticks) {
            this.blendIn = ticks;
            return this;
        }

        /** Glides back to the player's own view over the last of these ticks. */
        public Builder blendOut(float ticks) {
            this.blendOut = ticks;
            return this;
        }

        /** Whether the HUD and the player's hand are hidden while it plays; they are unless set. */
        public Builder hideHud(boolean hide) {
            this.hideHud = hide;
            return this;
        }

        /** Whether the player is held still while it plays; they are unless set. */
        public Builder lockInput(boolean lock) {
            this.lockInput = lock;
            return this;
        }

        /** Whether the player can skip it by holding the skip key; they can unless set. */
        public Builder skippable(boolean skippable) {
            this.skippable = skippable;
            return this;
        }

        /**
         * Whether players can open it in the in-game editor; they can unless set. Turn it off for a cutscene that
         * is part of a mod's story. Registered from code, such a cutscene can be neither replaced by a resource
         * pack nor copied through the editor.
         */
        public Builder editable(boolean editable) {
            this.editable = editable;
            return this;
        }

        /**
         * @throws IllegalArgumentException with no keyframes, two at the same time, or one keyframe and no duration
         */
        public Cutscene build() {
            if (this.keyframes.isEmpty()) throw new IllegalArgumentException("A cutscene needs at least one keyframe");
            float lastTime = 0.0F;
            for (CameraKeyframe keyframe : this.keyframes) lastTime = Math.max(lastTime, keyframe.time());
            float length = this.duration > 0.0F ? this.duration : lastTime;
            ValueTrack fades = this.fade;
            if (fades.isEmpty() && (this.fadeIn > 0.0F || this.fadeOut > 0.0F)) {
                float in = Math.min(this.fadeIn, length);
                float out = Math.min(this.fadeOut, length - in);
                if (in > 0.0F) fades = fades.then(0.0F, 1.0F).then(in, 0.0F);
                else fades = fades.then(0.0F, 0.0F);
                if (out > 0.0F) fades = fades.then(length - out, 0.0F).then(length, 1.0F);
            }
            return new Cutscene(this.keyframes, this.duration > 0.0F ? Optional.of(this.duration) : Optional.empty(), this.anchor, this.letterbox, this.bars,
                    fades, this.fadeColor, this.focuses, this.subtitles, this.markers, this.blendIn, this.blendOut, this.hideHud, this.lockInput, this.skippable, this.editable);
        }
    }
}
