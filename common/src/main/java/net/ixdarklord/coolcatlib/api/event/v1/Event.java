package net.ixdarklord.coolcatlib.api.event.v1;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Event<T> {
    private final List<Consumer<T>> listeners = new ArrayList<>();

    public Event() {
        // NO-OP
    }

    public void register(Consumer<T> listener) {
        listeners.add(listener);
    }

    public void unregister(Consumer<T> listener) {
        listeners.remove(listener);
    }

    public void invoke(T event) {
        for (Consumer<T> listener : listeners) {
            listener.accept(event);
        }
    }

}