package net.ixdarklord.coolcatcanvas.api.client.armature;

import net.ixdarklord.coolcatcanvas.api.client.armature.molang.MolangExpr;
import net.ixdarklord.coolcatcanvas.api.armature.LoopMode;
import net.ixdarklord.coolcatcanvas.api.armature.Motion;
import net.ixdarklord.coolcatcanvas.api.armature.Transition;
import net.ixdarklord.coolcatcanvas.api.utils.Easing;
import net.ixdarklord.coolcatcanvas.internal.client.armature.anim.ClipSource;

// One clip playing on a layer: where it is, how many times it has looped, and how far it has blended in or out.
final class Playback {
    final Motion motion;
    final LoopMode loop;
    Clip clip;
    double time;
    double delay;
    int loops;
    int direction = 1;
    boolean finished;

    // How it came in, kept for when it leaves the same way.
    final Transition transition;
    float fadeIn;
    float fadeInElapsed;
    Easing inEasing;
    boolean fadingOut;
    float fadeOut;
    float fadeOutElapsed;
    Easing outEasing;
    // A freeze transition holds it still while it fades out.
    boolean frozen;
    float weightAtFadeOut;
    float weight;

    Playback(Motion motion, Clip clip, Transition transition) {
        this.motion = motion;
        this.clip = clip;
        this.loop = motion.loop() != null ? motion.loop() : clip.loop();
        this.time = Math.max(0.0F, Math.min(motion.startAt(), clip.length()));
        if (motion.speed() < 0.0F && motion.startAt() <= 0.0F) this.time = clip.length();
        this.delay = clip.startDelay();
        this.transition = transition;
        this.fadeIn = transition.blends() ? transition.duration() : 0.0F;
        this.inEasing = transition.easing();
        this.outEasing = transition.easing();
    }

    /** Its blend weight now, before its layer's own weight. */
    float blendWeight() {
        float w;
        if (this.fadingOut) {
            float progress = this.fadeOut <= 0.0F ? 1.0F : Math.min(1.0F, this.fadeOutElapsed / this.fadeOut);
            w = this.weightAtFadeOut * (1.0F - this.outEasing.apply(progress));
        } else {
            float progress = this.fadeIn <= 0.0F ? 1.0F : Math.min(1.0F, this.fadeInElapsed / this.fadeIn);
            w = this.inEasing.apply(progress);
        }
        return Math.max(0.0F, Math.min(1.0F, w));
    }

    boolean fadedOut() {
        return this.fadingOut && this.fadeOutElapsed >= this.fadeOut;
    }

    void startFadeOut(float seconds) {
        this.startFadeOut(seconds, this.outEasing, false);
    }

    /** Blends it out over the next animation's transition: its curve, and held still if the transition freezes. */
    void startFadeOut(Transition next) {
        this.startFadeOut(next.blends() ? next.duration() : 0.0F, next.easing(), next.mode() == Transition.Mode.FREEZE);
    }

    private void startFadeOut(float seconds, Easing easing, boolean freeze) {
        if (this.fadingOut) return;
        this.weightAtFadeOut = this.blendWeight();
        this.fadingOut = true;
        this.fadeOut = seconds;
        this.fadeOutElapsed = 0.0F;
        this.outEasing = easing;
        this.frozen = freeze;
    }

    /** Seconds it takes to leave when it ends with nothing after it: as its motion asks, else as it came in. */
    float leaveTime() {
        return this.motion.fadeOutAsked() >= 0.0F ? this.motion.fadeOutAsked() : this.fadeIn;
    }

    /** Moves time on, calling {@code events} for every event passed. Returns true the moment it finishes. */
    boolean advance(double deltaTime, ArmatureContext context, float layerSpeed, EventSink events) {
        if (this.fadingOut) this.fadeOutElapsed += (float) deltaTime;
        else this.fadeInElapsed += (float) deltaTime;
        if (this.finished || this.frozen) return false;
        double speed = this.motion.speed() * layerSpeed;
        if (this.delay > 0.0) {
            this.delay -= deltaTime * Math.abs(speed);
            if (this.delay > 0.0) return false;
            deltaTime = -this.delay / Math.max(1.0E-6, Math.abs(speed));
            this.delay = 0.0;
        }
        double length = this.clip.length();
        double from = this.time;
        double to;
        MolangExpr update = this.clip.animTimeUpdate();
        if (update != null) {
            context.setAnim(this.time, length);
            to = update.eval(context);
        } else {
            to = from + deltaTime * speed * this.direction;
        }
        boolean forward = to >= from;
        switch (this.loop) {
            case ONCE, HOLD -> {
                if (to >= length || to <= 0.0 && !forward) {
                    to = forward ? length : 0.0;
                    this.fire(from, to, true, events);
                    this.time = to;
                    this.finished = true;
                    return true;
                }
                this.fire(from, to, false, events);
            }
            case LOOP -> {
                if (length <= 0.0) {
                    this.time = 0.0;
                    return false;
                }
                if (forward) {
                    while (to >= length) {
                        this.fire(from, length, true, events);
                        to -= length;
                        from = 0.0;
                        this.loops++;
                        if (this.motion.repeat() > 0 && this.loops >= this.motion.repeat()) {
                            this.time = length;
                            this.finished = true;
                            return true;
                        }
                        if (this.clip.loopDelay() > 0.0F) {
                            this.delay = this.clip.loopDelay();
                            to = 0.0;
                            break;
                        }
                    }
                } else {
                    while (to < 0.0) {
                        this.fire(from, 0.0, true, events);
                        to += length;
                        from = length;
                        this.loops++;
                        if (this.motion.repeat() > 0 && this.loops >= this.motion.repeat()) {
                            this.time = 0.0;
                            this.finished = true;
                            return true;
                        }
                    }
                }
                this.fire(from, to, false, events);
            }
            case PING_PONG -> {
                if (length <= 0.0) {
                    this.time = 0.0;
                    return false;
                }
                if (to >= length) {
                    this.fire(from, length, true, events);
                    from = length;
                    to = Math.max(0.0, 2.0 * length - to);
                    this.direction = -this.direction;
                } else if (to <= 0.0 && !forward) {
                    this.fire(from, 0.0, true, events);
                    from = 0.0;
                    to = Math.min(length, -to);
                    this.direction = -this.direction;
                    this.loops++;
                    if (this.motion.repeat() > 0 && this.loops >= this.motion.repeat()) {
                        this.time = 0.0;
                        this.finished = true;
                        return true;
                    }
                }
                this.fire(from, to, false, events);
            }
        }
        this.time = to;
        return false;
    }

    // Events from 'from' (counted) up to 'to' (not counted, so the next stretch counts it), backwards if to < from. The
    // end is counted where the clip stops or wraps there.
    private void fire(double from, double to, boolean inclusiveEnd, EventSink events) {
        ClipSource.Event[] all = this.clip.events();
        if (all.length == 0 || from == to && !inclusiveEnd) return;
        double lo = Math.min(from, to), hi = Math.max(from, to);
        boolean forward = to >= from;
        for (ClipSource.Event event : all) {
            double t = event.time();
            boolean inside = forward
                    ? t >= lo && (t < hi || inclusiveEnd && t == hi)
                    : t <= hi && (t > lo || inclusiveEnd && t == lo);
            if (inside) events.fire(this, event);
        }
    }

    @FunctionalInterface
    interface EventSink {
        void fire(Playback playback, ClipSource.Event event);
    }
}
