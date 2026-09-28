package net.ixdarklord.coolcatcore.internal.config;

import com.google.gson.JsonElement;
import net.ixdarklord.coolcatcore.api.config.ConfigDependency;
import net.ixdarklord.coolcatcore.api.config.ConfigScope;
import net.ixdarklord.coolcatcore.api.config.ConfigValue;
import net.ixdarklord.coolcatcore.api.config.RestartRequirement;
import net.ixdarklord.coolcatcore.api.config.StartupSync;
import net.ixdarklord.coolcatcore.api.config.type.ConfigType;
import net.ixdarklord.coolcatcore.api.config.type.ValidationResult;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

public final class ConfigValueImpl<T> extends ConfigNodeImpl implements ConfigValue<T> {
    private final ConfigType<T> type;
    private final T defaultValue;
    private final RestartRequirement restart;
    private final boolean synced;
    private final StartupSync startupSync;
    private final List<String> aliases;
    private final List<Function<T, Component>> validators;
    private final @Nullable ConfigDependency<?> dependency;
    private final List<ChangeListener<T>> listeners = new CopyOnWriteArrayList<>();
    private volatile T value;

    public ConfigValueImpl(String key, ConfigGroupImpl parent, List<String> comment, @Nullable String translationKey, boolean hidden,
                           ConfigType<T> type, T defaultValue, RestartRequirement restart, boolean synced, StartupSync startupSync, List<String> aliases,
                           List<Function<T, Component>> validators, @Nullable ConfigDependency<?> dependency) {
        super(key, parent, comment, translationKey, hidden);
        this.type = type;
        this.restart = restart;
        this.synced = synced;
        this.startupSync = startupSync;
        this.aliases = List.copyOf(aliases);
        this.validators = List.copyOf(validators);
        this.dependency = dependency;
        ValidationResult<T> result = this.validate(defaultValue);
        if (!result.isOk()) {
            throw new IllegalArgumentException("Invalid default value for " + key + ": " + result.messageString());
        }
        this.defaultValue = result.value();
        this.value = this.defaultValue;
    }

    @Override
    public T get() {
        return this.value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T getStored() {
        ConfigImpl config = this.configOrNull();
        return config == null ? this.value : (T) config.stored(this);
    }

    @Override
    public void set(T value) {
        ValidationResult<T> result = this.validate(value);
        if (result.isError()) {
            throw new IllegalArgumentException("Invalid value for " + this.path() + ": " + result.messageString());
        }
        ConfigImpl config = this.configOrNull();
        if (config == null) {
            // Before the config is built there's nothing to notify.
            this.value = result.value();
        } else {
            config.applyChanges(Map.of(this, result.value()));
        }
    }

    @Override
    public ValidationResult<T> validate(T value) {
        if (value == null) return ValidationResult.error(Component.translatableWithFallback("config.coolcatcore.error.null", "Must not be empty"));
        ValidationResult<T> result = this.type.validate(value);
        for (Function<T, Component> validator : this.validators) {
            result = result.then(checked -> {
                Component error = validator.apply(checked);
                return error == null ? ValidationResult.ok(checked) : ValidationResult.error(error);
            });
        }
        return result;
    }

    /** Validates a value of unknown type, as the config screen and network hand them over. */
    @SuppressWarnings("unchecked")
    public ValidationResult<T> validateUnchecked(Object value) {
        try {
            return this.validate((T) value);
        } catch (ClassCastException e) {
            return ValidationResult.error(Component.literal("Wrong value type " + value.getClass().getSimpleName()));
        }
    }

    public ValidationResult<T> decode(JsonElement json) {
        return ConfigJson.decode(this.type.codec(), json).then(this::validate);
    }

    public JsonElement encode(T value) {
        return ConfigJson.encode(this.type.codec(), value);
    }

    public JsonElement encodeCurrent() {
        return this.encode(this.value);
    }

    @SuppressWarnings("unchecked")
    public JsonElement encodeUnchecked(Object value) {
        return this.encode((T) value);
    }

    @SuppressWarnings("unchecked")
    public boolean sameValue(Object first, Object second) {
        return this.type.equals((T) first, (T) second);
    }

    // Stores a value that was already validated; returns the previous one.
    @SuppressWarnings("unchecked")
    T swap(Object newValue) {
        T old = this.value;
        this.value = (T) newValue;
        return old;
    }

    @SuppressWarnings("unchecked")
    void fireListeners(Object oldValue, Object newValue) {
        for (ChangeListener<T> listener : this.listeners) {
            try {
                listener.onChanged((T) oldValue, (T) newValue);
            } catch (RuntimeException e) {
                CoolCatCore.LOGGER.error("A listener of config value {} ({}) failed", this.path(), this.config().id(), e);
            }
        }
    }

    @Override
    public T getDefault() {
        return this.defaultValue;
    }

    @Override
    public ConfigType<T> type() {
        return this.type;
    }

    @Override
    public RestartRequirement restartRequirement() {
        // Startup values are fixed until the game restarts.
        ConfigImpl config = this.configOrNull();
        return config != null && config.scope() == ConfigScope.STARTUP ? RestartRequirement.GAME : this.restart;
    }

    @Override
    public boolean isSynced() {
        return this.synced;
    }

    @Override
    public StartupSync startupSync() {
        return this.startupSync;
    }

    @Override
    public List<String> aliases() {
        return this.aliases;
    }

    @Override
    public Optional<ConfigDependency<?>> dependency() {
        return Optional.ofNullable(this.dependency);
    }

    @Override
    public void addListener(ChangeListener<T> listener) {
        this.listeners.add(listener);
    }

    @Override
    public boolean removeListener(ChangeListener<T> listener) {
        return this.listeners.remove(listener);
    }

    @Override
    public String toString() {
        ConfigImpl config = this.configOrNull();
        return "ConfigValue[" + (config == null ? "?" : config.id()) + " " + this.path() + "=" + this.value + "]";
    }
}
