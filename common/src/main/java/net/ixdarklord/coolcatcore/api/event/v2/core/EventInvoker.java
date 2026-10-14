package net.ixdarklord.coolcatcore.api.event.v2.core;

import net.ixdarklord.coolcatcore.internal.event.EventInvokerImpl;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.function.Function;

/**
 * One cross-loader event, identified by its listener type: a functional interface.
 * <p>
 * Listeners are kept here, in common code, and run in {@link EventPhase} order; each loader only fires the event
 * through {@link #invoker()}. Events are found by their listener type, like
 * <pre>{@code
 * EventInvoker.lookup(ServerTickEvents.End.class).register(server -> ...);
 * }</pre>
 * or through the constant of the class that declares them ({@code ServerTickEvents.END}); both are the same event.
 * <p>
 * Mods declare their own events with {@link #create(Class)}:
 * <pre>{@code
 * public static final EventInvoker<Reloaded> RELOADED = EventInvoker.create(Reloaded.class);
 * RELOADED.invoker().onReloaded(server);
 * }</pre>
 *
 * @param <T> the listener type
 */
@ApiStatus.NonExtendable
public interface EventInvoker<T> {

    /**
     * The event with this listener type. The class declaring the listener type (for nested types) is initialised
     * first, so its events exist.
     *
     * @throws IllegalArgumentException when no event has this listener type
     */
    static <T> EventInvoker<T> lookup(Class<T> type) {
        return EventInvokerImpl.lookup(type);
    }

    /**
     * A new event, with listeners combined by the listener method's return type:
     * <ul>
     *     <li>{@code void}: every listener runs;</li>
     *     <li>{@link EventResult}: listeners run until one interrupts, whose result is returned (else {@link EventResult#PASS});</li>
     *     <li>{@link EventResultHolder}: the same, with its value;</li>
     *     <li>{@code boolean}: listeners run until one returns {@code false}.</li>
     * </ul>
     * Use {@link #create(Class, Function)} for anything else, or for a hot event that shouldn't go through reflection.
     *
     * @throws IllegalArgumentException when the type isn't a functional interface, or already has an event
     */
    static <T> EventInvoker<T> create(Class<T> type) {
        return EventInvokerImpl.create(type, null);
    }

    /**
     * A new event whose invoker is built by {@code combiner} from the current listeners, in phase order. The
     * combiner runs again whenever the listeners change, so the invoker it returns can simply loop over the list.
     *
     * @throws IllegalArgumentException when the type already has an event
     */
    static <T> EventInvoker<T> create(Class<T> type, Function<List<T>, T> combiner) {
        return EventInvokerImpl.create(type, combiner);
    }

    /**
     * The listener type identifying this event.
     */
    Class<T> type();

    /**
     * Adds a listener in the {@link EventPhase#DEFAULT} phase.
     */
    default void register(T listener) {
        this.register(EventPhase.DEFAULT, listener);
    }

    /**
     * Adds a listener in the given phase.
     */
    void register(EventPhase phase, T listener);

    /**
     * Removes a listener, from whichever phase it was added to.
     *
     * @return whether it was registered
     */
    boolean unregister(T listener);

    /**
     * @return whether the event has any listeners, so a loader can skip building its arguments
     */
    boolean hasListeners();

    /**
     * Fires the event: calls every listener as the event combines them.
     */
    T invoker();
}
