package net.ixdarklord.coolcatcore.internal.event;

import net.ixdarklord.coolcatcore.api.event.v2.core.EventInvoker;
import net.ixdarklord.coolcatcore.api.event.v2.core.EventPhase;
import net.ixdarklord.coolcatcore.api.event.v2.core.EventResult;
import net.ixdarklord.coolcatcore.api.event.v2.core.EventResultHolder;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class EventInvokerImpl<T> implements EventInvoker<T> {
    private static final Map<Class<?>, EventInvokerImpl<?>> EVENTS = new ConcurrentHashMap<>();

    private final Class<T> type;
    private final Function<List<T>, T> combiner;
    // Listeners in phase order (stable within a phase); the invoker is rebuilt from them on every change.
    private final List<Entry<T>> entries = new ArrayList<>();
    private volatile T invoker;
    private volatile boolean hasListeners;

    private EventInvokerImpl(Class<T> type, Function<List<T>, T> combiner) {
        this.type = type;
        this.combiner = combiner;
        this.invoker = combiner.apply(List.of());
    }

    public static <T> EventInvoker<T> create(Class<T> type, @Nullable Function<List<T>, T> combiner) {
        Objects.requireNonNull(type, "type is null");
        EventInvokerImpl<T> event = new EventInvokerImpl<>(type, combiner != null ? combiner : reflectiveCombiner(type));
        if (EVENTS.putIfAbsent(type, event) != null) {
            throw new IllegalArgumentException("An event already exists for " + type.getName());
        }
        return event;
    }

    @SuppressWarnings("unchecked")
    public static <T> EventInvoker<T> lookup(Class<T> type) {
        Objects.requireNonNull(type, "type is null");
        EventInvokerImpl<?> event = EVENTS.get(type);
        if (event == null && type.getEnclosingClass() != null) {
            // Nested listener types don't initialise the class declaring their event; do it now.
            try {
                Class.forName(type.getEnclosingClass().getName(), true, type.getClassLoader());
            } catch (ClassNotFoundException ignored) {
            }
            event = EVENTS.get(type);
        }
        if (event == null) throw new IllegalArgumentException("No event exists for " + type.getName());
        return (EventInvoker<T>) event;
    }

    @Override
    public Class<T> type() {
        return this.type;
    }

    @Override
    public synchronized void register(EventPhase phase, T listener) {
        Objects.requireNonNull(phase, "phase is null");
        Objects.requireNonNull(listener, "listener is null");
        int index = this.entries.size();
        // After every listener of this phase or an earlier one.
        while (index > 0 && this.entries.get(index - 1).phase.compareTo(phase) > 0) index--;
        this.entries.add(index, new Entry<>(phase, listener));
        this.rebuild();
    }

    @Override
    public synchronized boolean unregister(T listener) {
        boolean removed = this.entries.removeIf(entry -> entry.listener == listener);
        if (removed) this.rebuild();
        return removed;
    }

    @Override
    public boolean hasListeners() {
        return this.hasListeners;
    }

    @Override
    public T invoker() {
        return this.invoker;
    }

    private void rebuild() {
        List<T> listeners = this.entries.stream().map(Entry::listener).toList();
        this.invoker = this.combiner.apply(listeners);
        this.hasListeners = !listeners.isEmpty();
    }

    private record Entry<T>(EventPhase phase, T listener) {}

    // --- The default combiner for create(Class): a proxy calling each listener in turn ---

    @SuppressWarnings("unchecked")
    private static <T> Function<List<T>, T> reflectiveCombiner(Class<T> type) {
        if (!type.isInterface()) throw new IllegalArgumentException(type.getName() + " isn't an interface");
        Method method = Arrays.stream(type.getMethods())
                .filter(m -> Modifier.isAbstract(m.getModifiers()))
                .reduce((a, b) -> {
                    throw new IllegalArgumentException(type.getName() + " isn't a functional interface");
                })
                .orElseThrow(() -> new IllegalArgumentException(type.getName() + " has no listener method"));
        Class<?> returnType = method.getReturnType();
        Object passValue = returnType == void.class ? null
                : returnType == EventResult.class ? EventResult.PASS
                : returnType == EventResultHolder.class ? EventResultHolder.pass()
                : returnType == boolean.class ? Boolean.TRUE
                : null;
        if (returnType != void.class && passValue == null) {
            throw new IllegalArgumentException(type.getName() + " returns " + returnType.getName() + "; give EventInvoker.create a combiner for it");
        }
        return listeners -> {
            Object[] array = listeners.toArray();
            InvocationHandler handler = (proxy, invoked, args) -> {
                if (invoked.getDeclaringClass() == Object.class) return objectMethod(proxy, invoked, args, type);
                for (Object listener : array) {
                    Object result;
                    try {
                        result = invoked.invoke(listener, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                    if (result instanceof EventResult eventResult && eventResult.isInterrupt()) return result;
                    if (result instanceof EventResultHolder<?> holder && holder.isInterrupt()) return result;
                    if (result instanceof Boolean bool && !bool) return result;
                }
                return passValue;
            };
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
        };
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args, Class<?> type) {
        return switch (method.getName()) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "EventInvoker[" + type.getName() + "]";
            default -> throw new UnsupportedOperationException(method.getName());
        };
    }
}
