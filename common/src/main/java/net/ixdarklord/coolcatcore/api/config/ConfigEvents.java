package net.ixdarklord.coolcatcore.api.config;

import net.ixdarklord.coolcatcore.api.event.v2.core.EventInvoker;

import java.util.Set;

/**
 * Every config's lifecycle, for code watching configs it didn't declare. A config's own listeners
 * ({@link Config#addListener}, {@link ConfigValue#addListener}) are usually simpler.
 * <pre>{@code
 * ConfigEvents.LOADED.register(config -> LOGGER.info("Loaded {}", config.id()));
 * ConfigEvents.RELOADED.register(config -> rebuildCaches());
 * ConfigEvents.UNLOADING.register(config -> saveWorldState(config));
 * ConfigEvents.VALUE_CHANGED.register((config, value, oldValue, newValue) -> {
 *     if (value == MyConfig.RENDER_DISTANCE) refreshRenderer();
 * });
 * }</pre>
 * In order, a reload that changes values fires {@link #VALUE_CHANGED} for each value, then {@link #CHANGED} once,
 * then {@link #RELOADED}. Listeners run on the thread that caused them: the server or client thread for commands,
 * screens, syncs and hot reloads.
 */
public final class ConfigEvents {
    public static final EventInvoker<Loaded> LOADED = EventInvoker.create(Loaded.class, listeners -> config -> {
        for (Loaded listener : listeners) listener.onLoaded(config);
    });
    public static final EventInvoker<Reloaded> RELOADED = EventInvoker.create(Reloaded.class, listeners -> config -> {
        for (Reloaded listener : listeners) listener.onReloaded(config);
    });
    public static final EventInvoker<Unloading> UNLOADING = EventInvoker.create(Unloading.class, listeners -> config -> {
        for (Unloading listener : listeners) listener.onUnloading(config);
    });
    public static final EventInvoker<ValueChanged> VALUE_CHANGED = EventInvoker.create(ValueChanged.class, listeners -> (config, value, oldValue, newValue) -> {
        for (ValueChanged listener : listeners) listener.onValueChanged(config, value, oldValue, newValue);
    });
    public static final EventInvoker<Saved> SAVED = EventInvoker.create(Saved.class, listeners -> config -> {
        for (Saved listener : listeners) listener.onSaved(config);
    });
    public static final EventInvoker<Changed> CHANGED = EventInvoker.create(Changed.class, listeners -> (config, changed) -> {
        for (Changed listener : listeners) listener.onChanged(config, changed);
    });
    public static final EventInvoker<Synced> SYNCED = EventInvoker.create(Synced.class, listeners -> config -> {
        for (Synced listener : listeners) listener.onSynced(config);
    });

    private ConfigEvents() {}

    /**
     * The file was read for the first time: when the config is registered, or for a world config when a server starts
     * (every time one starts). A missing file was written with the defaults first.
     */
    @FunctionalInterface
    public interface Loaded {
        void onLoaded(Config config);
    }

    /**
     * The file was read again after loading: edited on disk (hot reload), the reload command, or {@link Config#reload()}.
     * Values that changed have already fired {@link #VALUE_CHANGED} and {@link #CHANGED}.
     */
    @FunctionalInterface
    public interface Reloaded {
        void onReloaded(Config config);
    }

    /**
     * A world config is about to go back to its defaults because its server stopped. Its values are still the world's
     * here; the reset that follows fires {@link #VALUE_CHANGED} and {@link #CHANGED} as usual.
     */
    @FunctionalInterface
    public interface Unloading {
        void onUnloading(Config config);
    }

    /**
     * One value changed, by any means: set in code, a screen or command, a reload, the server's values arriving, or a
     * reset. It fires for each value before {@link #CHANGED} fires once for them all. A startup config's changes only
     * fire when they apply (the next start), not when they're stored.
     */
    @FunctionalInterface
    public interface ValueChanged {
        void onValueChanged(Config config, ConfigValue<?> value, Object oldValue, Object newValue);
    }

    /** The file was written. */
    @FunctionalInterface
    public interface Saved {
        void onSaved(Config config);
    }

    /** Values changed, by any means, on the thread that changed them. */
    @FunctionalInterface
    public interface Changed {
        void onChanged(Config config, Set<ConfigValue<?>> changed);
    }

    /** Client only: the server's values of a synced config arrived. */
    @FunctionalInterface
    public interface Synced {
        void onSynced(Config config);
    }
}
