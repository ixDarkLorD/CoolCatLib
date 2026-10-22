package net.ixdarklord.coolcatcanvas.internal.client.armature;

import com.google.common.collect.MapMaker;
import net.ixdarklord.coolcatcanvas.api.client.armature.Animator;
import net.ixdarklord.coolcatcanvas.api.client.armature.Armature;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The animator of every entity, block entity and item stack drawn with an armature, held only as long as the thing itself
 * (weak keys, compared by identity). Commands for one not drawn yet (a server's "play" for an entity just coming into
 * view) wait a moment for it.
 */
public final class ArmatureInstances {
    private static final long COMMAND_LIFETIME_NANOS = 1_000_000_000L;
    private static final Map<Object, Animator> ANIMATORS = new MapMaker().weakKeys().makeMap();
    private static final Map<Object, List<Pending>> PENDING = new MapMaker().weakKeys().makeMap();

    private ArmatureInstances() {
    }

    /** The owner's animator, made and set up the first time (and again if it is now drawn with another armature). */
    public static Animator get(Object owner, Armature armature, Consumer<Animator> setup) {
        Animator animator = ANIMATORS.get(owner);
        if (animator == null || !animator.armature().equals(armature)) {
            animator = new Animator(armature, owner);
            setup.accept(animator);
            ANIMATORS.put(owner, animator);
            List<Pending> waiting = PENDING.remove(owner);
            if (waiting != null) {
                long now = Util.getNanos();
                for (Pending pending : waiting) {
                    if (now - pending.time < COMMAND_LIFETIME_NANOS) pending.command.accept(animator);
                }
            }
        }
        return animator;
    }

    public static @Nullable Animator find(Object owner) {
        return ANIMATORS.get(owner);
    }

    /** Runs a command on the owner's animator now, or as soon as it has one if that is within a second. */
    public static void command(Object owner, Consumer<Animator> command) {
        Animator animator = ANIMATORS.get(owner);
        if (animator != null) command.accept(animator);
        else PENDING.computeIfAbsent(owner, o -> new ArrayList<>()).add(new Pending(command, Util.getNanos()));
    }

    public static void clear() {
        ANIMATORS.clear();
        PENDING.clear();
    }

    private record Pending(Consumer<Animator> command, long time) {
    }
}
