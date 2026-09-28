package net.ixdarklord.coolcatcore.api.event.v2.core;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * When a listener runs relative to the others of the same event. Listeners run from the lowest {@link #order()} to the
 * highest; listeners in the same phase run in the order they were registered.
 * <p>
 * The built-in phases leave room between them, so a mod can slot its own in with {@link #of(String, int)}.
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

    @Override
    public int compareTo(@NotNull EventPhase other) {
        return Integer.compare(this.order, other.order);
    }
}
