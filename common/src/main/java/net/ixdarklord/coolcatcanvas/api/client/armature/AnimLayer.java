package net.ixdarklord.coolcatcanvas.api.client.armature;

import net.ixdarklord.coolcatcanvas.api.armature.LoopMode;
import net.ixdarklord.coolcatcanvas.api.armature.Motion;
import net.ixdarklord.coolcatcanvas.api.armature.Transition;
import net.ixdarklord.coolcatcanvas.internal.client.armature.anim.ClipSource;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * One track of animation on an {@link Animator}: it plays one clip at a time, blending smoothly from the last, over
 * the joints in its {@linkplain JointMask mask}. Layers are stacked in the order they were made, later ones on top.
 *
 * <p>A layer can be <b>driven</b>: a function asked every frame which motion should play, which the layer follows,
 * blending whenever the answer changes. {@link #play} plays something regardless; once it ends (a
 * {@link LoopMode#ONCE} clip, a repeat count, or {@link #stop}) the driver takes over again.</p>
 * <pre>{@code
 * animator.layer("base").drive(ctx -> ctx.moving() ? WALK : IDLE);
 * animator.layer("arms", l -> l.mask(JointMask.of("arm_left", "arm_right")));
 * animator.layer("breath", l -> l.blend(LayerBlend.ADDITIVE).weight(0.6F)).play(Motion.loop("breathe"));
 * animator.layer("arms").play(Motion.once("wave"));
 * }</pre>
 */
public final class AnimLayer {
    private static final Set<String> WARNED = new HashSet<>();

    private final String name;
    private final Animator animator;
    private LayerBlend blend = LayerBlend.OVERRIDE;
    private JointMask mask = JointMask.all();
    private float weight = 1.0F;
    private float targetWeight = 1.0F;
    private float weightRate;
    private float speed = 1.0F;
    private @Nullable Function<ArmatureContext, @Nullable Motion> driver;
    private boolean manual;
    private @Nullable Transition transition;
    private @Nullable TransitionRule rule;
    private final List<PairRule> pairs = new ArrayList<>();
    private boolean transitions = true;

    private @Nullable Playback current;
    private final List<Playback> fading = new ArrayList<>();

    AnimLayer(String name, Animator animator) {
        this.name = name;
        this.animator = animator;
    }

    public String name() {
        return this.name;
    }

    // ---- Setup ----

    public AnimLayer blend(LayerBlend blend) {
        this.blend = blend;
        return this;
    }

    public AnimLayer mask(JointMask mask) {
        this.mask = mask;
        return this;
    }

    /** How strongly it shows, 0 to 1, at once. */
    public AnimLayer weight(float weight) {
        this.weight = this.targetWeight = Mth.clamp(weight, 0.0F, 1.0F);
        this.weightRate = 0.0F;
        return this;
    }

    /** Moves its weight to a new value over some seconds: fade a layer in or out as a whole. */
    public AnimLayer fadeWeight(float weight, float seconds) {
        this.targetWeight = Mth.clamp(weight, 0.0F, 1.0F);
        this.weightRate = seconds <= 0.0F ? 0.0F : Math.abs(this.targetWeight - this.weight) / seconds;
        if (seconds <= 0.0F) this.weight = this.targetWeight;
        return this;
    }

    public float weight() {
        return this.weight;
    }

    /** Plays everything on this layer faster or slower, on top of each motion's own speed. */
    public AnimLayer speed(float speed) {
        this.speed = speed;
        return this;
    }

    /**
     * Has the layer follow a function, asked every frame: whatever motion it returns plays, blending in when the answer
     * changes; null blends back to rest (or to the layers below). Asking for the motion that is already playing changes
     * nothing, so return constants, not new motions each time.
     */
    public AnimLayer drive(Function<ArmatureContext, @Nullable Motion> driver) {
        this.driver = driver;
        return this;
    }

    // ---- Transitions ----

    /**
     * How animations on this layer blend into each other unless a rule or the motion says otherwise. Without one, the
     * animator's default is used ({@link Animator#transition}), and without that {@link Transition#DEFAULT}.
     */
    public AnimLayer transition(Transition transition) {
        this.transition = transition;
        return this;
    }

    /**
     * How one clip blends into another on this layer: {@code transition("run", "idle", Transition.freeze(0.4F))}.
     * Either name may be {@code "*"} for any clip; {@code from} is {@code "*"} too when nothing was playing. The most
     * exact rule wins (both names, then {@code from}, then {@code to}), then the last one given.
     */
    public AnimLayer transition(String from, String to, Transition transition) {
        this.pairs.add(new PairRule(from, to, transition));
        return this;
    }

    /** Picks transitions with code, before the pair rules and the default; return null to fall through to them. */
    public AnimLayer transitions(TransitionRule rule) {
        this.rule = rule;
        return this;
    }

    /**
     * Turns blending on this layer on or off: off, every change cuts straight to the next animation, whatever the
     * motions and rules say. On by default.
     */
    public AnimLayer transitions(boolean enabled) {
        this.transitions = enabled;
        return this;
    }

    public boolean transitionsEnabled() {
        return this.transitions;
    }

    /** The transition the next change to this motion would use, by the rules above. */
    public Transition transitionTo(Motion motion) {
        if (!this.transitions || !this.animator.transitionsEnabled()) return Transition.CUT;
        if (motion.transition() != null) return motion.transition();
        String from = this.current != null && !this.current.fadingOut ? this.current.clip.name() : null;
        if (this.rule != null) {
            Transition chosen = this.rule.choose(from, motion, this.animator.context());
            if (chosen != null) return chosen;
        }
        Transition best = null;
        int bestScore = 0;
        for (PairRule pair : this.pairs) {
            int score = pair.score(from, motion.clip());
            if (score > 0 && score >= bestScore) {
                best = pair.transition;
                bestScore = score;
            }
        }
        if (best != null) return best;
        if (this.transition != null) return this.transition;
        return this.animator.defaultTransition();
    }

    private record PairRule(String from, String to, Transition transition) {
        // 0 when it doesn't apply; else higher the more exactly it names the change.
        int score(@Nullable String fromClip, String toClip) {
            boolean anyFrom = this.from.equals("*"), anyTo = this.to.equals("*");
            if (!anyFrom && !this.from.equals(fromClip)) return 0;
            if (!anyTo && !this.to.equals(toClip)) return 0;
            return 1 + (anyFrom ? 0 : 2) + (anyTo ? 0 : 1);
        }
    }

    // ---- Playing ----

    /**
     * Plays a motion now, setting the driver aside until it ends. Playing the clip that is already playing does
     * nothing unless the motion {@linkplain Motion#restart() restarts}.
     */
    public AnimLayer play(Motion motion) {
        this.manual = true;
        if (this.current != null && !this.current.finished && !this.current.fadingOut && this.current.motion.sameAs(motion) && !motion.restarts()) return this;
        this.start(motion);
        return this;
    }

    /** Plays a clip the way its file says to. */
    public AnimLayer play(String clip) {
        return this.play(Motion.of(clip));
    }

    /** Blends out what {@link #play} started, back to the driver or to rest, the way it blended in. */
    public AnimLayer stop() {
        return this.stop(this.current != null ? this.leaveTime(this.current) : Motion.DEFAULT_FADE);
    }

    public AnimLayer stop(float fadeSeconds) {
        this.manual = false;
        if (this.current != null) {
            this.current.startFadeOut(fadeSeconds);
            this.fading.add(this.current);
            this.current = null;
        }
        return this;
    }

    // ---- Reading ----

    /** Whether a clip is playing and has not ended (a held last frame counts as ended). */
    public boolean isPlaying() {
        return this.current != null && !this.current.finished;
    }

    public boolean isPlaying(String clip) {
        return this.isPlaying() && this.current.clip.name().equals(clip);
    }

    /** The clip playing, or null. */
    public @Nullable String clip() {
        return this.current == null ? null : this.current.clip.name();
    }

    /** Seconds into the clip playing. */
    public float time() {
        return this.current == null ? 0.0F : (float) this.current.time;
    }

    /** How far through the clip playing, 0 to 1. */
    public float progress() {
        if (this.current == null || this.current.clip.length() <= 0.0F) return 0.0F;
        return (float) (this.current.time / this.current.clip.length());
    }

    // ---- Animator's side ----

    private void start(Motion motion) {
        BakedArmature armature = this.animator.armatureData();
        Clip clip = armature.clip(motion.clip());
        if (clip == null) {
            if (!armature.isEmpty()) warnOnce("Armature " + armature.id() + " has no clip named \"" + motion.clip() + "\" (layer " + this.name + ")");
            return;
        }
        Transition transition = this.transitionTo(motion);
        if (this.current != null) {
            this.current.startFadeOut(transition);
            this.fading.add(this.current);
        }
        if (!transition.blends()) this.fading.clear();
        this.current = new Playback(motion, clip, transition);
    }

    void update(double deltaTime, ArmatureContext context) {
        if (this.weight != this.targetWeight) {
            float step = this.weightRate * (float) deltaTime;
            this.weight = this.weight < this.targetWeight ? Math.min(this.targetWeight, this.weight + step) : Math.max(this.targetWeight, this.weight - step);
        }
        if (this.driver != null && !this.manual) this.follow(context);

        Playback playing = this.current;
        if (playing != null && playing.advance(deltaTime, context, this.speed, this::fire)) {
            Motion next = playing.motion.next();
            if (next != null) {
                this.start(next);
            } else if (playing.loop != LoopMode.HOLD) {
                this.manual = false;
                if (this.driver != null) this.follow(context);
                if (this.current == playing) this.stop(this.leaveTime(playing));
            }
        }
        for (int i = this.fading.size() - 1; i >= 0; i--) {
            Playback fading = this.fading.get(i);
            fading.advance(deltaTime, context, this.speed, (p, e) -> {});
            if (fading.fadedOut()) this.fading.remove(i);
        }
    }

    private void follow(ArmatureContext context) {
        Motion wanted = this.driver.apply(context);
        if (wanted == null) {
            if (this.current != null) this.stop(this.leaveTime(this.current));
            this.manual = false;
        } else if (this.current == null || this.current.fadingOut || !this.current.motion.sameAs(wanted) || this.current.finished && wanted.loop() != LoopMode.HOLD && this.current.loop != LoopMode.HOLD) {
            this.start(wanted);
        }
    }

    // Seconds to blend back to rest: none with transitions off, else as the motion asks or as it came in.
    private float leaveTime(Playback playback) {
        return !this.transitions || !this.animator.transitionsEnabled() ? 0.0F : playback.leaveTime();
    }

    private void fire(Playback playback, ClipSource.Event event) {
        this.animator.fire(this, playback, event);
    }

    // When a resource reload replaced the armature: find each clip again by name, or drop it.
    void rebind(BakedArmature armature) {
        if (this.current != null) {
            Clip clip = armature.clip(this.current.clip.name());
            if (clip == null) this.current = null;
            else this.current.clip = clip;
        }
        this.fading.removeIf(p -> {
            Clip clip = armature.clip(p.clip.name());
            if (clip != null) p.clip = clip;
            return clip == null;
        });
    }

    // Blends this layer into the pose, using the animator's scratch arrays.
    void apply(ArmaturePose pose, ArmatureContext context, float[] value, float[] cover, float[] sample) {
        if (this.weight <= 0.0F || this.current == null && this.fading.isEmpty()) return;
        int n = pose.armature.jointCount();
        if (n == 0) return;
        java.util.Arrays.fill(value, 0, n * 12, 0.0F);
        java.util.Arrays.fill(cover, 0, n * 4, 0.0F);

        float total = 0.0F;
        for (Playback p : this.fading) {
            p.weight = p.blendWeight();
            total += p.weight;
        }
        if (this.current != null) {
            this.current.weight = this.current.blendWeight();
            total += this.current.weight;
        }
        float normal = total > 1.0F ? 1.0F / total : 1.0F;
        boolean[] mask = this.mask.resolve(pose.armature);
        for (int i = 0; i <= this.fading.size(); i++) {
            Playback p = i < this.fading.size() ? this.fading.get(i) : this.current;
            if (p == null) continue;
            float w = p.weight * normal;
            if (p.clip.blendWeight() != null) {
                context.setAnim(p.time, p.clip.length());
                w *= Mth.clamp(p.clip.blendWeight().evalFloat(context), 0.0F, 1.0F);
            }
            if (w <= 0.0F || p.clip.jointCount() != n) continue;
            context.setAnim(p.time, p.clip.length());
            for (int j = 0; j < n; j++) {
                if (!mask[j]) continue;
                ClipSource.Channels channels = p.clip.channels(j);
                if (channels == null) continue;
                if (channels.rotation != null) {
                    channels.rotation.sample(p.time, context, sample);
                    value[j * 12] += -sample[0] * Mth.DEG_TO_RAD * w;
                    value[j * 12 + 1] += -sample[1] * Mth.DEG_TO_RAD * w;
                    value[j * 12 + 2] += sample[2] * Mth.DEG_TO_RAD * w;
                    cover[j * 4] += w;
                }
                if (channels.position != null) {
                    channels.position.sample(p.time, context, sample);
                    value[j * 12 + 3] += -sample[0] * w;
                    value[j * 12 + 4] += sample[1] * w;
                    value[j * 12 + 5] += sample[2] * w;
                    cover[j * 4 + 1] += w;
                }
                if (channels.scale != null) {
                    channels.scale.sample(p.time, context, sample);
                    value[j * 12 + 6] += sample[0] * w;
                    value[j * 12 + 7] += sample[1] * w;
                    value[j * 12 + 8] += sample[2] * w;
                    cover[j * 4 + 2] += w;
                }
                if (channels.bend != null) {
                    // Bend: how far, in degrees, and which way round the limb (0 is forward).
                    channels.bend.sample(p.time, context, sample);
                    value[j * 12 + 9] += sample[0] * Mth.DEG_TO_RAD * w;
                    value[j * 12 + 10] += sample[1] * Mth.DEG_TO_RAD * w;
                    cover[j * 4 + 3] += w;
                }
            }
        }

        float layer = this.weight;
        boolean additive = this.blend == LayerBlend.ADDITIVE;
        for (int j = 0; j < n; j++) {
            for (int channel = 0; channel < 4; channel++) {
                float c = cover[j * 4 + channel];
                if (c <= 0.0F) continue;
                float[] target = channel == 0 ? pose.rotation : channel == 1 ? pose.position : channel == 2 ? pose.scale : pose.bend;
                if (!additive) pose.cover[j * 4 + channel] = 1.0F - (1.0F - pose.cover[j * 4 + channel]) * (1.0F - Math.min(1.0F, c * layer));
                for (int axis = 0; axis < 3; axis++) {
                    float v = value[j * 12 + channel * 3 + axis];
                    int at = j * 3 + axis;
                    if (!additive) target[at] = target[at] * (1.0F - c * layer) + v * layer;
                    else if (channel == 2) target[at] *= 1.0F + (v - c) * layer;
                    else target[at] += v * layer;
                }
            }
        }
    }

    private static void warnOnce(String message) {
        synchronized (WARNED) {
            if (WARNED.add(message)) CoolCatCanvas.LOGGER.warn(message);
        }
    }
}
