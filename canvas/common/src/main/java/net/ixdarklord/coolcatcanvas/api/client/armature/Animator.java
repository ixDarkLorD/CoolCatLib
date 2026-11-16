package net.ixdarklord.coolcatcanvas.api.client.armature;

import net.ixdarklord.coolcatcanvas.api.armature.Motion;
import net.ixdarklord.coolcatcanvas.api.armature.Transition;
import net.ixdarklord.coolcatcanvas.api.client.vfx.ParticleSpec;
import net.ixdarklord.coolcatcanvas.internal.client.armature.ArmatureEffects;
import net.ixdarklord.coolcatcanvas.internal.client.armature.anim.ClipSource;
import net.minecraft.util.ARGB;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Everything one armature-driven thing is doing: its animation layers, procedural motion, joint styles and Molang variables.
 * Every entity, block entity and item stack drawn with an armature has its own, made the first time it is drawn and set up
 * by its renderer.
 * <pre>{@code
 * animator.layer("base").drive(ctx -> ctx.moving() ? WALK : IDLE);
 * animator.layer("action", l -> l.mask(JointMask.of("body")));
 * animator.lookAt("head");
 * animator.spring("tail").damping(6);
 * animator.onEvent(event -> { if (event.effect().equals("stomp")) shakeCamera(); });
 * animator.hide("saddle");
 * }</pre>
 * Only {@link #play(Motion)} and {@link #layer(String)} are needed for the simplest setups: a layer named
 * {@value #BASE} is made when first asked for, and {@code play} uses it.
 */
public final class Animator {
    /** The layer {@link #play(Motion)} and server commands without a layer use. */
    public static final String BASE = "base";

    private final Armature armature;
    private final ArmatureContext context;
    private final Map<String, AnimLayer> layers = new LinkedHashMap<>();
    private final List<JointModifier> modifiers = new ArrayList<>();
    private final List<ArmatureEvent.Listener> listeners = new ArrayList<>();
    private final Map<String, LookAt> lookAts = new LinkedHashMap<>();
    private final Map<String, Spring> springs = new LinkedHashMap<>();
    private final Map<String, Integer> tints = new HashMap<>();
    private final Map<String, Boolean> glows = new HashMap<>();
    private final Map<String, Boolean> hidden = new HashMap<>();
    private boolean builtInEffects = true;
    private Transition transition = Transition.DEFAULT;
    private boolean transitions = true;
    private final List<ObjProp> props = new ArrayList<>();
    private final Map<String, ArmatureTrail> trails = new LinkedHashMap<>();
    private final List<ArmatureEmitter> emitters = new ArrayList<>();

    private BakedArmature baked;
    private @Nullable ArmaturePose pose;
    private @Nullable ArmatureFrame frame;
    private Spring[] springByJoint = new Spring[0];
    private float[] value = new float[0];
    private float[] cover = new float[0];
    private final float[] sample = new float[3];
    private double time;
    private double lastClock = Double.NaN;

    public Animator(Armature armature, Object owner) {
        this.armature = armature;
        this.context = new ArmatureContext(owner);
        this.baked = armature.get();
        // What the armature's .armature.json asks for comes first, so the renderer's own setup can change it.
        for (Consumer<Animator> setup : this.baked.defaults()) setup.accept(this);
    }

    public Armature armature() {
        return this.armature;
    }

    public ArmatureContext context() {
        return this.context;
    }

    BakedArmature armatureData() {
        return this.baked;
    }

    // ---- Layers ----

    /** A layer by name, made (on top of the others) if there is none yet. */
    public AnimLayer layer(String name) {
        return this.layers.computeIfAbsent(name, n -> new AnimLayer(n, this));
    }

    /** A layer, set up by {@code setup} only when it is first made. */
    public AnimLayer layer(String name, Consumer<AnimLayer> setup) {
        AnimLayer layer = this.layers.get(name);
        if (layer == null) {
            layer = this.layer(name);
            setup.accept(layer);
        }
        return layer;
    }

    public @Nullable AnimLayer findLayer(String name) {
        return this.layers.get(name);
    }

    /** Every layer, bottom first. */
    public List<AnimLayer> layers() {
        return Collections.unmodifiableList(new ArrayList<>(this.layers.values()));
    }

    /** Plays a motion on the {@value #BASE} layer. */
    public Animator play(Motion motion) {
        this.layer(BASE).play(motion);
        return this;
    }

    public Animator play(String layer, Motion motion) {
        this.layer(layer).play(motion);
        return this;
    }

    /** How animations blend into each other on layers that set no transition of their own. */
    public Animator transition(Transition transition) {
        this.transition = transition;
        return this;
    }

    /** Turns blending on or off for every layer at once: off, every change cuts. On by default. */
    public Animator transitions(boolean enabled) {
        this.transitions = enabled;
        return this;
    }

    public boolean transitionsEnabled() {
        return this.transitions;
    }

    Transition defaultTransition() {
        return this.transition;
    }

    public Animator stop(String layer) {
        AnimLayer found = this.layers.get(layer);
        if (found != null) found.stop();
        return this;
    }

    /** Whether any layer is playing this clip. */
    public boolean isPlaying(String clip) {
        for (AnimLayer layer : this.layers.values()) {
            if (layer.isPlaying(clip)) return true;
        }
        return false;
    }

    // ---- Procedural motion ----

    /** Turns a joint toward where the entity looks (or the nearest player); made once, then the same one is returned. */
    public LookAt lookAt(String joint) {
        return this.lookAts.computeIfAbsent(joint, LookAt::new);
    }

    /** Makes a joint swing on a spring; made once, then the same one is returned. */
    public Spring spring(String joint) {
        return this.springs.computeIfAbsent(joint, j -> {
            this.springByJoint = new Spring[0];
            return new Spring(j);
        });
    }

    /** Carries an {@code .obj} model on a joint or point, as well as the ones its {@code .armature.json} gives. */
    public Animator prop(ObjProp prop) {
        this.props.add(prop);
        return this;
    }

    public Animator removeProp(ObjProp prop) {
        this.props.remove(prop);
        return this;
    }

    /** A glowing trail behind a point; made once, then the same one is returned. */
    public ArmatureTrail trail(String point) {
        return this.trails.computeIfAbsent(point, ArmatureTrail::new);
    }

    /** Effect particles pouring from a point. Several can share a point. */
    public ArmatureEmitter emitter(String point, ParticleSpec spec) {
        ArmatureEmitter emitter = new ArmatureEmitter(point, spec);
        this.emitters.add(emitter);
        return emitter;
    }

    /** Runs code on the pose every frame, after the layers and head tracking. */
    public Animator modify(JointModifier modifier) {
        this.modifiers.add(modifier);
        return this;
    }

    /** Gets every sound, particle and instruction keyframe as it plays. */
    public Animator onEvent(ArmatureEvent.Listener listener) {
        this.listeners.add(listener);
        return this;
    }

    /** Whether sound and particle keyframes play by themselves (they do unless turned off here). */
    public Animator builtInEffects(boolean play) {
        this.builtInEffects = play;
        return this;
    }

    // ---- Joint styles (kept until changed) ----

    /** Hides a joint and everything on it until {@link #show}n. */
    public Animator hide(String joint) {
        this.hidden.put(joint, true);
        return this;
    }

    public Animator show(String joint) {
        this.hidden.remove(joint);
        return this;
    }

    public Animator setVisible(String joint, boolean visible) {
        return visible ? this.show(joint) : this.hide(joint);
    }

    /** Multiplies a joint's colour (and its children's) by an ARGB colour; white clears it. */
    public Animator tint(String joint, int argb) {
        if (argb == -1) this.tints.remove(joint);
        else this.tints.put(joint, argb);
        return this;
    }

    /** Draws a joint and its children at full brightness, whatever the light. */
    public Animator glow(String joint, boolean glow) {
        if (glow) this.glows.put(joint, true);
        else this.glows.remove(joint);
        return this;
    }

    // ---- Variables ----

    public double variable(String name) {
        return this.context.variable(name);
    }

    public Animator setVariable(String name, double value) {
        this.context.setVariable(name, value);
        return this;
    }

    // ---- Updating ----

    /** The pose last worked out (what the layers, head tracking and modifiers made), or null before the first update. */
    public @Nullable ArmaturePose pose() {
        return this.pose;
    }

    /** The frame last worked out, or null before the first update. */
    public @Nullable ArmatureFrame frame() {
        return this.frame;
    }

    /**
     * Moves everything on to a moment and poses the armature. Renderers call this each frame; call it yourself only to draw
     * an armature somewhere CoolCatLib's renderers don't reach.
     *
     * @param clock        seconds on any clock that only goes forward ({@code GameClock}-like); the first call starts it
     * @param modelToWorld from model pixels to the world, in blocks, for springs, head tracking and particles
     */
    public ArmatureFrame update(double clock, float partialTick, Matrix4fc modelToWorld) {
        BakedArmature current = this.armature.get();
        if (current != this.baked || this.pose == null) this.rebind(current);
        double delta = Double.isNaN(this.lastClock) ? 0.0 : Math.max(0.0, Math.min(0.25, clock - this.lastClock));
        this.lastClock = clock;
        this.time += delta;
        this.context.advance(this.time, delta, partialTick);

        ArmaturePose pose = this.pose;
        ArmatureFrame frame = this.frame;
        frame.modelToWorld.set(modelToWorld);
        for (AnimLayer layer : this.layers.values()) layer.update(delta, this.context);
        pose.reset();
        for (AnimLayer layer : this.layers.values()) layer.apply(pose, this.context, this.value, this.cover, this.sample);
        for (LookAt look : this.lookAts.values()) look.apply(pose, this.context, modelToWorld, (float) delta);
        for (JointModifier modifier : this.modifiers) modifier.modify(pose, this.context);
        this.pose(current, pose, frame, modelToWorld, (float) delta);
        frame.props.clear();
        frame.props.addAll(current.props());
        frame.props.addAll(this.props);
        // Trails and emitters need a place in the world, which items in slots and hands don't have.
        if (this.context.stack().isEmpty() && (!this.trails.isEmpty() || !this.emitters.isEmpty())) {
            for (ArmatureTrail trail : this.trails.values()) trail.feed(frame, this.context);
            for (ArmatureEmitter emitter : this.emitters) emitter.emit(frame, this.context, delta);
        }
        return frame;
    }

    private void rebind(BakedArmature armature) {
        this.baked = armature;
        this.pose = new ArmaturePose(armature);
        this.frame = new ArmatureFrame(armature);
        this.value = new float[armature.jointCount() * 12];
        this.cover = new float[armature.jointCount() * 4];
        this.springByJoint = new Spring[0];
        for (AnimLayer layer : this.layers.values()) layer.rebind(armature);
        for (Spring spring : this.springs.values()) spring.reset();
    }

    // Joint matrices, parents first, with springs swinging as they go; then who is shown and how.
    private void pose(BakedArmature armature, ArmaturePose pose, ArmatureFrame frame, Matrix4fc modelToWorld, float delta) {
        int n = armature.jointCount();
        if (this.springByJoint.length != n) {
            this.springByJoint = new Spring[n];
            for (Spring spring : this.springs.values()) {
                int index = armature.jointIndex(spring.joint());
                if (index >= 0) this.springByJoint[index] = spring;
            }
            // The springs that bump each know the others.
            Spring[] bumpers = this.springs.values().stream().filter(Spring::bumps).toArray(Spring[]::new);
            for (Spring spring : bumpers) spring.peers = bumpers;
            // And the springs woven together, each to its own.
            for (Spring spring : this.springs.values()) {
                spring.linked = spring.links().stream().map(this.springs::get).filter(other -> other != null && other != spring).toArray(Spring[]::new);
            }
            // And each spring the one above it, on its joint's parent, to pull on.
            for (int j = 0; j < n; j++) {
                Spring spring = this.springByJoint[j];
                int parent = armature.joint(j).parent();
                if (spring != null) spring.above = parent >= 0 ? this.springByJoint[parent] : null;
            }
        }
        boolean[] hidden = this.resolve(armature, this.hidden);
        boolean[] glows = this.resolve(armature, this.glows);
        for (int j = 0; j < n; j++) {
            Joint joint = armature.joint(j);
            int parent = joint.parent();
            float px = joint.pivot().x(), py = joint.pivot().y(), pz = joint.pivot().z();
            Matrix4f m = frame.joints[j];
            if (parent >= 0) m.set(frame.joints[parent]);
            else m.identity();
            int r = j * 3;
            m.translate(px + pose.position[r], py + pose.position[r + 1], pz + pose.position[r + 2])
                    .rotateZYX(joint.restRotation().z() + pose.rotation[r + 2], joint.restRotation().y() + pose.rotation[r + 1], joint.restRotation().x() + pose.rotation[r])
                    .scale(pose.scale[r], pose.scale[r + 1], pose.scale[r + 2])
                    .translate(-px, -py, -pz);
            Spring spring = this.springByJoint[j];
            if (spring != null && spring.enabled() && delta >= 0.0F) spring.apply(armature, j, m, modelToWorld, delta, frame.joints, this.context);
            m.normal(frame.normals[j]);

            boolean parentShown = parent < 0 || frame.visible[parent];
            frame.visible[j] = parentShown && !hidden[j] && !pose.hidden[j];
            frame.glow[j] = glows[j] || parent >= 0 && frame.glow[parent];
            Integer tint = this.tints.isEmpty() ? null : this.tints.get(joint.name());
            int inherited = parent >= 0 ? frame.tint[parent] : -1;
            frame.tint[j] = tint == null ? inherited : ARGB.multiply(inherited, tint);
        }
    }

    private boolean[] resolve(BakedArmature armature, Map<String, Boolean> byName) {
        boolean[] out = new boolean[armature.jointCount()];
        for (String name : byName.keySet()) {
            int index = armature.jointIndex(name);
            if (index >= 0) out[index] = true;
        }
        return out;
    }

    void fire(AnimLayer layer, Playback playback, ClipSource.Event event) {
        this.context.setAnim(event.time(), playback.clip.length());
        if (event.script() != null) event.script().eval(this.context);
        ArmatureEvent.Kind kind = switch (event.kind()) {
            case SOUND -> ArmatureEvent.Kind.SOUND;
            case PARTICLE -> ArmatureEvent.Kind.PARTICLE;
            case INSTRUCTION -> ArmatureEvent.Kind.INSTRUCTION;
        };
        ArmatureEvent fired = new ArmatureEvent(kind, layer.name(), playback.clip.name(), event.effect(), event.locator(), event.time(), this.context);
        if (this.builtInEffects && kind != ArmatureEvent.Kind.INSTRUCTION) ArmatureEffects.play(fired, this.frame);
        for (ArmatureEvent.Listener listener : this.listeners) listener.onEvent(fired);
    }
}
