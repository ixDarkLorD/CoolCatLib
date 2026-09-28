package net.ixdarklord.coolcatcore.api.handler;

import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The handlers one holder hands out, by type and side.
 * <pre>{@code
 * handlers.add(ENERGY, this.battery);                                 // every side
 * handlers.addSided(HandlerTypes.CONTAINER, side -> side == Direction.DOWN ? output : input);
 * }</pre>
 * If what a holder hands out changes after it's placed, call
 * {@link net.ixdarklord.coolcatcore.api.block.ExtendedBlockEntity#invalidateHandlers()} so caches look again.
 */
public final class HandlerMap {
    private final Map<HandlerType<?>, Factory<?>> factories = new LinkedHashMap<>();

    /** The same handler on every side. */
    public <T> HandlerMap add(HandlerType<T> type, T handler) {
        Objects.requireNonNull(handler, "handler");
        return this.addSided(type, side -> handler);
    }

    /** A handler per side; {@code factory} may return {@code null} for sides without one. */
    public <T> HandlerMap addSided(HandlerType<T> type, Factory<T> factory) {
        this.factories.put(type, Objects.requireNonNull(factory, "factory"));
        return this;
    }

    public HandlerMap remove(HandlerType<?> type) {
        this.factories.remove(type);
        return this;
    }

    public boolean has(HandlerType<?> type) {
        return this.factories.containsKey(type);
    }

    @SuppressWarnings("unchecked")
    public <T> @Nullable T get(HandlerType<T> type, @Nullable Direction side) {
        Factory<T> factory = (Factory<T>) this.factories.get(type);
        return factory == null ? null : factory.get(side);
    }

    @FunctionalInterface
    public interface Factory<T> {
        @Nullable T get(@Nullable Direction side);
    }
}
