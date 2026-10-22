package net.ixdarklord.coolcatcore.api.event.v2.core;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * When a listener runs relative to the others of the same event. Listeners run from the lowest {@link #order()} to the
 * highest; listeners in the same phase run in the order they were registered.
 * <p>
 * The built-in phases leave room between them, so a mod can slot its own in with {@link #of(String, int)}.
 * <p>
 * Phases also place a listener among other mods' own listeners of the same game event (their NeoForge or Fabric
 * listeners, which CoolCatLib doesn't run), by {@link #band()}: {@link #FIRST} and earlier run before them,
 * {@link #LAST} and later after them, and the phases between with them. Only events whose loader hook fires each band
 * at its own place do this (their documentation says so); for the others every phase runs at once, in order.
 */
public record EventPhase(@NotNull String name, int order) implements Comparable<EventPhase> {
    public static final EventPhase FIRST = new EventPhase("first", -2000);
    public static final EventPhase EARLY = new EventPhase("early", -1000);
    public static final EventPhase DEFAULT = new EventPhase("default", 0);
    public static final EventPhase LATE = new EventPhase("late", 1000);
    public static final EventPhase LAST = new EventPhase("last", 2000);

    public EventPhase {
        Objects.requireNonNull(name, "name is null");
    }

    /**
     * A custom phase, e.g. {@code EventPhase.of("after_defaults", 500)}.
     */
    public static EventPhase of(String name, int order) {
        return new EventPhase(name, order);
    }

    /**
     * A phase running just before this one.
     */
    public EventPhase before() {
        return new EventPhase("before_" + this.name, this.order - 1);
    }

    /**
     * A phase running just after this one.
     */
    public EventPhase after() {
        return new EventPhase("after_" + this.name, this.order + 1);
    }

    /** Where listeners of this phase run among other mods' own listeners of the game event. */
    public Band band() {
        if (this.order <= FIRST.order) return Band.FIRST;
        if (this.order >= LAST.order) return Band.LAST;
        return Band.MIDDLE;
    }

    /**
     * Where a phase's listeners run among other mods' own listeners of the game event: before them (NeoForge's highest
     * priority, a Fabric phase before the default one), with them, or after them (the lowest priority, a phase after
     * the default one).
     */
    public enum Band {
        FIRST,
        MIDDLE,
        LAST
    }

    @Override
    public int compareTo(@NotNull EventPhase other) {
        return Integer.compare(this.order, other.order);
    }
}
