package net.ixdarklord.coolcatcanvas.internal.client.armature.anim;

import net.ixdarklord.coolcatcanvas.api.client.armature.molang.MolangExpr;
import net.ixdarklord.coolcatcanvas.api.armature.LoopMode;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An animation as a file describes it, with its joints by name; {@code Clip.bind} turns it into one armature's clip. The
 * same file can animate any armature whose joints share the names.
 */
public final class ClipSource {
    public final String name;
    public float length = -1.0F;
    public LoopMode loop = LoopMode.ONCE;
    public @Nullable MolangExpr animTimeUpdate;
    public @Nullable MolangExpr blendWeight;
    public float startDelay;
    public float loopDelay;
    public final Map<String, Channels> joints = new LinkedHashMap<>();
    public final List<Event> events = new ArrayList<>();

    public ClipSource(String name) {
        this.name = name;
    }

    public Channels joint(String name) {
        return this.joints.computeIfAbsent(name, n -> new Channels());
    }

    /** The clip's length: as the file says, else its last keyframe or event. */
    public float resolvedLength() {
        if (this.length >= 0.0F) return this.length;
        float last = 0.0F;
        for (Channels channels : this.joints.values()) {
            if (channels.rotation != null) last = Math.max(last, channels.rotation.lastTime());
            if (channels.position != null) last = Math.max(last, channels.position.lastTime());
            if (channels.scale != null) last = Math.max(last, channels.scale.lastTime());
            if (channels.bend != null) last = Math.max(last, channels.bend.lastTime());
        }
        for (Event event : this.events) last = Math.max(last, event.time);
        return last;
    }

    /** One joint's tracks; any may be missing, and a missing channel leaves the joint to other layers or its rest. */
    public static final class Channels {
        public @Nullable Track rotation;
        public @Nullable Track position;
        public @Nullable Track scale;
        /** Bending along the joint's length: amount and direction in degrees (a {@code bend} channel). */
        public @Nullable Track bend;
    }

    public enum EventKind { SOUND, PARTICLE, INSTRUCTION }

    /**
     * Something a clip does at a moment: a sound, a particle, or an instruction (Molang to run, which listeners also
     * get as text).
     */
    public record Event(float time, EventKind kind, String effect, @Nullable String locator, @Nullable MolangExpr script) {
    }
}
