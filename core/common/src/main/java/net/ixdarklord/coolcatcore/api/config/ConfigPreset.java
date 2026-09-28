package net.ixdarklord.coolcatcore.api.config;

import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A named set of values applied together, like "Performance" or "Quality". Presets are declared on the
 * {@link ConfigBuilder}, and chosen from the config screen or with {@code /coolcatcore config <id> preset <name>}.
 */
public final class ConfigPreset {
    private final String name;
    private final Component displayName;
    private final Map<ConfigValue<?>, Object> values;

    ConfigPreset(String name, Component displayName, Map<ConfigValue<?>, Object> values) {
        this.name = name;
        this.displayName = displayName;
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public String name() {
        return this.name;
    }

    public Component displayName() {
        return this.displayName;
    }

    /** The values the preset sets; everything else keeps its value. */
    public Map<ConfigValue<?>, Object> values() {
        return this.values;
    }

    /** Collects a preset's values. */
    public static final class Builder {
        private final Map<ConfigValue<?>, Object> values = new LinkedHashMap<>();

        Builder() {}

        /**
         * @throws IllegalArgumentException when the value isn't valid for the entry
         */
        public <T> Builder set(ConfigValue<T> value, T presetValue) {
            var result = value.validate(presetValue);
            if (!result.isOk()) {
                throw new IllegalArgumentException("Invalid preset value for " + value.path() + ": " + result.messageString());
            }
            this.values.put(value, result.value());
            return this;
        }

        /** Sets the value back to its default in this preset. */
        public <T> Builder setDefault(ConfigValue<T> value) {
            return this.set(value, value.getDefault());
        }

        Map<ConfigValue<?>, Object> values() {
            return this.values;
        }
    }
}
