package net.ixdarklord.coolcatcanvas.internal.client.config;

import net.ixdarklord.coolcatcore.api.config.Config;
import net.ixdarklord.coolcatcore.api.config.ConfigValue;
import net.ixdarklord.coolcatcore.api.config.type.ValidationResult;
import net.ixdarklord.coolcatcore.internal.config.client.ClientConfigManager;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

// The changes made in a category popup: pending values, applied on save (like the config screen's: saved here, or
// sent to the server for a server config edited there), and invalid input.
final class CategoryEditSession {
    private final Config config;
    private final Map<ConfigValue<?>, Object> pending = new LinkedHashMap<>();
    private final Map<ConfigValue<?>, Component> errors = new HashMap<>();

    CategoryEditSession(Config config) {
        this.config = config;
    }

    Config config() {
        return this.config;
    }

    /** Whether the config can be changed from here: a server config shown while playing on one may be read only. */
    boolean isEditable() {
        ClientConfigManager.Access access = ClientConfigManager.access(this.config);
        return access == ClientConfigManager.Access.LOCAL || access == ClientConfigManager.Access.REMOTE;
    }

    @SuppressWarnings("unchecked")
    <T> T get(ConfigValue<T> value) {
        return (T) this.pending.getOrDefault(value, value.getStored());
    }

    boolean isModified(ConfigValue<?> value) {
        return this.pending.containsKey(value) || this.errors.containsKey(value);
    }

    <T> boolean isDefault(ConfigValue<T> value) {
        return value.type().equals(this.get(value), value.getDefault());
    }

    Optional<Component> error(ConfigValue<?> value) {
        return Optional.ofNullable(this.errors.get(value));
    }

    int modifiedCount() {
        return this.pending.size();
    }

    boolean hasErrors() {
        return !this.errors.isEmpty();
    }

    Collection<Component> errors() {
        return this.errors.values();
    }

    /** Whether the value's dependency is met by the pending values. */
    boolean isActive(ConfigValue<?> value) {
        return value.dependency().map(dependency -> {
            ConfigValue<?> source = dependency.source();
            Object sourceValue = source.config() == this.config ? this.pending.getOrDefault(source, source.getStored()) : source.get();
            return dependency.isMetBy(sourceValue);
        }).orElse(true);
    }

    /** Sets a pending value, checked by the value's validators; invalid values are reported instead. */
    <T> void set(ConfigValue<T> value, T newValue) {
        ValidationResult<T> result = value.validate(newValue);
        if (result.isError()) {
            this.errors.put(value, result.message().orElse(Component.empty()));
            return;
        }
        this.errors.remove(value);
        if (value.type().equals(newValue, value.getStored())) this.pending.remove(value);
        else this.pending.put(value, newValue);
    }

    void resetToDefaults(Collection<ConfigValue<?>> values) {
        values.forEach(this::reset);
    }

    private <T> void reset(ConfigValue<T> value) {
        this.set(value, value.getDefault());
    }

    /** Applies the pending values. */
    void save() {
        if (this.pending.isEmpty() || this.hasErrors() || !this.isEditable()) return;
        StyledConfigScreen.apply(this.pending);
        this.pending.clear();
    }
}
