package net.ixdarklord.coolcatlib.api.event.v2.core;

import com.google.common.base.Suppliers;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.function.Supplier;

/**
 * EventInvoker provides a common event handling mechanism for different mod loaders.
 * It allows registering and looking up events dynamically based on the event type.
 *
 * @param <T> the event type
 */
@FunctionalInterface
public interface EventInvoker<T> {

    /**
     * A synchronized map storing registered event invokers.
     */
    Map<Class<?>, EventInvokerLike<?>> EVENT_INVOKER_LOOKUP = Collections.synchronizedMap(Maps.newIdentityHashMap());

    /**
     * A queue for deferred invoker registrations to ensure they are processed when initialization completes.
     */
    Queue<Runnable> DEFERRED_INVOKER_REGISTRATIONS = Queues.newConcurrentLinkedQueue();

    /**
     * Indicates whether the event system has been initialized.
     */
    boolean initialized = false;

    /**
     * Retrieves an event invoker for the specified event type.
     *
     * @param clazz the event type class
     * @param <T>   the event type
     * @return the event invoker instance
     */
    static <T> EventInvoker<T> lookup(Class<T> clazz) {
        return lookup(clazz, null);
    }

    /**
     * Retrieves an event invoker for the specified event type with an optional context.
     *
     * @param clazz   the event type class
     * @param context additional context for the lookup
     * @param <T>     the event type
     * @return the event invoker instance
     */
    static <T> EventInvoker<T> lookup(Class<T> clazz, @Nullable Object context) {
        return softLookup(clazz, context);
    }

    /**
     * Performs a soft lookup for an event invoker, deferring registration if necessary.
     *
     * @param clazz   the event type class
     * @param context additional context for the lookup
     * @param <T>     the event type
     * @return the event invoker instance
     */
    static <T> EventInvoker<T> softLookup(Class<T> clazz, @Nullable Object context) {
        Objects.requireNonNull(clazz, "type is null");
        Supplier<EventInvoker<T>> invoker = Suppliers.memoize(() -> lookupInvoker(clazz, context));
        return (phase, callback) -> {
            if (!initialized && !EVENT_INVOKER_LOOKUP.containsKey(clazz)) {
                DEFERRED_INVOKER_REGISTRATIONS.offer(() -> invoker.get().register(phase, callback));
            } else {
                invoker.get().register(phase, callback);
            }
        };
    }

    /**
     * Internal method to look up an event invoker.
     *
     * @param clazz   the event type class
     * @param context additional context for the lookup
     * @param <T>     the event type
     * @return the event invoker instance
     */
    @SuppressWarnings("unchecked")
    private static <T> EventInvoker<T> lookupInvoker(Class<T> clazz, @Nullable Object context) {
        Objects.requireNonNull(clazz, "type is null");
        EventInvokerLike<T> invokerLike = (EventInvokerLike<T>) EVENT_INVOKER_LOOKUP.get(clazz);
        Objects.requireNonNull(invokerLike, "invoker is null for type " + clazz);
        EventInvoker<T> invoker = invokerLike.asEventInvoker(context);
        Objects.requireNonNull(invoker, "invoker is null for type " + clazz);
        return invoker;
    }

    /**
     * Registers an event invoker for a specific event type.
     *
     * @param clazz        the event type class
     * @param invoker      the event invoker instance
     * @param joinInvokers whether to merge with an existing invoker
     * @param <T>          the event type
     */
    static <T> void register(Class<T> clazz, EventInvokerLike<T> invoker, boolean joinInvokers) {
        EventInvokerLike<T> other = (EventInvokerLike<T>) EVENT_INVOKER_LOOKUP.get(clazz);
        if (other != null) {
            if (joinInvokers) {
                invoker = join(invoker, other);
            } else {
                throw new IllegalArgumentException("Duplicate event invoker for type " + clazz);
            }
        }
        EVENT_INVOKER_LOOKUP.put(clazz, invoker);
    }

    /**
     * Joins two event invokers into one.
     *
     * @param invoker the first event invoker
     * @param other   the second event invoker
     * @param <T>     the event type
     * @return a merged event invoker
     */
    private static <T> EventInvokerLike<T> join(EventInvokerLike<T> invoker, EventInvokerLike<T> other) {
        return (context) -> (phase, callback) -> {
            invoker.asEventInvoker(context).register(phase, callback);
            other.asEventInvoker(context).register(phase, callback);
        };
    }

    /**
     * Registers an event callback with the default event phase.
     *
     * @param callback the event callback
     */
    default void register(T callback) {
        this.register(EventPhase.DEFAULT, callback);
    }

    /**
     * Registers an event callback with a specific event phase.
     *
     * @param phase    the event phase
     * @param callback the event callback
     */
    void register(EventPhase phase, T callback);

    /**
     * Represents an event invoker that can be converted into a standard EventInvoker.
     *
     * @param <T> the event type
     */
    @FunctionalInterface
    interface EventInvokerLike<T> {
        /**
         * Converts to an EventInvoker.
         *
         * @param context optional context for the invoker
         * @return an EventInvoker instance
         */
        EventInvoker<T> asEventInvoker(@Nullable Object context);
    }
}
