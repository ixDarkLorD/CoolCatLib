package net.ixdarklord.coolcatcanvas.api.client.armature;

import net.ixdarklord.coolcatcanvas.api.client.armature.molang.MolangExpr;
import net.ixdarklord.coolcatcanvas.api.armature.LoopMode;
import net.ixdarklord.coolcatcanvas.internal.client.armature.anim.ClipSource;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;

/** One animation of an armature, as its file describes it. Played through an {@link Animator}. */
public final class Clip {
    private final String name;
    private final float length;
    private final LoopMode loop;
    private final @Nullable MolangExpr animTimeUpdate;
    private final @Nullable MolangExpr blendWeight;
    private final float startDelay;
    private final float loopDelay;
    private final ClipSource.Channels[] channels;
    private final ClipSource.Event[] events;

    private Clip(ClipSource source, ClipSource.Channels[] channels) {
        this.name = source.name;
        this.length = source.resolvedLength();
        this.loop = source.loop;
        this.animTimeUpdate = source.animTimeUpdate;
        this.blendWeight = source.blendWeight;
        this.startDelay = Math.max(0.0F, source.startDelay);
        this.loopDelay = Math.max(0.0F, source.loopDelay);
        this.channels = channels;
        this.events = source.events.toArray(ClipSource.Event[]::new);
        Arrays.sort(this.events, Comparator.comparingDouble(ClipSource.Event::time));
    }

    /** The clip for one armature: its joints found by name. Channels for joints the armature doesn't have are dropped. */
    public static Clip bind(ClipSource source, Map<String, Integer> jointIndex, int jointCount) {
        ClipSource.Channels[] channels = new ClipSource.Channels[jointCount];
        source.joints.forEach((jointName, tracks) -> {
            Integer index = jointIndex.get(jointName);
            if (index != null) channels[index] = tracks;
        });
        return new Clip(source, channels);
    }

    public String name() {
        return this.name;
    }

    /** Seconds from start to end. */
    public float length() {
        return this.length;
    }

    /** How it loops unless a {@code Motion} says otherwise. */
    public LoopMode loop() {
        return this.loop;
    }

    /** Whether it moves this joint at all. */
    public boolean animates(int joint) {
        return joint < this.channels.length && this.channels[joint] != null;
    }

    public float startDelay() {
        return this.startDelay;
    }

    public float loopDelay() {
        return this.loopDelay;
    }

    @Nullable MolangExpr animTimeUpdate() {
        return this.animTimeUpdate;
    }

    @Nullable MolangExpr blendWeight() {
        return this.blendWeight;
    }

    @Nullable ClipSource.Channels channels(int joint) {
        return this.channels[joint];
    }

    int jointCount() {
        return this.channels.length;
    }

    ClipSource.Event[] events() {
        return this.events;
    }

    @Override
    public String toString() {
        return "Clip[" + this.name + ", " + this.length + "s, " + this.loop + "]";
    }
}
