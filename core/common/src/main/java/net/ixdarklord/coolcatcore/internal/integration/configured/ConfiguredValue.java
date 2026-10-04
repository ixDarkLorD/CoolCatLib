package net.ixdarklord.coolcatcore.internal.integration.configured;

import com.mrcrayfish.configured.api.IAllowedEnums;
import com.mrcrayfish.configured.api.IConfigValue;
import net.ixdarklord.coolcatcore.api.config.RestartRequirement;
import net.ixdarklord.coolcatcore.api.config.type.BooleanType;
import net.ixdarklord.coolcatcore.api.config.type.ConfigType;
import net.ixdarklord.coolcatcore.api.config.type.EnumType;
import net.ixdarklord.coolcatcore.api.config.type.NumberType;
import net.ixdarklord.coolcatcore.api.config.type.StringType;
import net.ixdarklord.coolcatcore.api.config.type.ValidationResult;
import net.ixdarklord.coolcatcore.internal.config.ConfigValueImpl;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A config value being edited in Configured's screen: the edit stays here until the config is saved. Configured has
 * widgets for booleans, ints, longs, doubles, enums and strings; every other type (colors, ids, lists, floats, codec
 * values...) is edited as the text its type reads and writes, as Core's commands do.
 */
abstract sealed class ConfiguredValue<V> implements IConfigValue<V> permits ConfiguredValue.Direct, ConfiguredValue.AsText {
    protected final ConfigValueImpl<?> value;

    ConfiguredValue(ConfigValueImpl<?> value) {
        this.value = value;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static ConfiguredValue<?> of(ConfigValueImpl<?> value) {
        ConfigType<?> type = value.type();
        if (type instanceof EnumType<?>) return new EnumValue((ConfigValueImpl) value);
        if (type instanceof BooleanType || type instanceof StringType) return new Direct<>(value);
        if (type instanceof NumberType<?> number && number.kind() != NumberType.FLOAT) return new Direct<>(value);
        return new AsText<>(value);
    }

    ConfigValueImpl<?> configValue() {
        return this.value;
    }

    /** The edited value as the config holds it, to apply on saving. */
    abstract Object pending();

    @Override
    public @Nullable Component getComment() {
        String key = this.value.translationKey() + ".tooltip";
        if (Language.getInstance().has(key)) return Component.translatable(key);
        return this.value.comment().isEmpty() ? null : Component.literal(String.join("\n", this.value.comment()));
    }

    @Override
    public String getTranslationKey() {
        return this.value.translationKey();
    }

    @Override
    public @Nullable Component getValidationHint() {
        List<Component> lines = this.value.type().describe();
        if (lines.isEmpty()) return null;
        MutableComponent hint = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) hint.append("\n");
            hint.append(lines.get(i));
        }
        return hint;
    }

    @Override
    public String getName() {
        return this.value.key();
    }

    @Override
    public boolean requiresWorldRestart() {
        return this.value.restartRequirement() == RestartRequirement.WORLD;
    }

    @Override
    public boolean requiresGameRestart() {
        return this.value.restartRequirement() == RestartRequirement.GAME;
    }

    // A value Configured has a widget for, edited as it is.
    static sealed class Direct<T> extends ConfiguredValue<T> permits EnumValue {
        private final ConfigValueImpl<T> typed;
        private T initial;
        private T current;

        Direct(ConfigValueImpl<T> value) {
            super(value);
            this.typed = value;
            this.initial = value.getStored();
            this.current = this.initial;
        }

        @Override
        public T get() {
            return this.current;
        }

        @Override
        public T getDefault() {
            return this.typed.getDefault();
        }

        @Override
        public void set(T value) {
            this.current = value;
        }

        @Override
        public boolean isValid(T value) {
            return value != null && !this.typed.validate(value).isError();
        }

        @Override
        public boolean isDefault() {
            return this.typed.type().equals(this.current, this.typed.getDefault());
        }

        @Override
        public boolean isChanged() {
            return !this.typed.type().equals(this.current, this.initial);
        }

        @Override
        public void restore() {
            this.current = this.typed.getDefault();
        }

        @Override
        public void cleanCache() {
            this.initial = this.current;
        }

        @Override
        Object pending() {
            ValidationResult<T> result = this.typed.validate(this.current);
            return result.hasValue() ? result.value() : this.current;
        }
    }

    // An enum, offering only its constants.
    static final class EnumValue<E extends Enum<E>> extends Direct<E> implements IAllowedEnums<E> {
        private final EnumType<E> type;

        EnumValue(ConfigValueImpl<E> value) {
            super(value);
            this.type = (EnumType<E>) value.type();
        }

        @Override
        public Set<E> getAllowedValues() {
            return new LinkedHashSet<>(this.type.constants());
        }
    }

    // Any other type, edited as text: the type formats the value and parses it back.
    static final class AsText<T> extends ConfiguredValue<String> {
        private final ConfigValueImpl<T> typed;
        private String initial;
        private String current;

        AsText(ConfigValueImpl<T> value) {
            super(value);
            this.typed = value;
            this.initial = value.type().format(value.getStored());
            this.current = this.initial;
        }

        @Override
        public String get() {
            return this.current;
        }

        @Override
        public String getDefault() {
            return this.typed.type().format(this.typed.getDefault());
        }

        @Override
        public void set(String value) {
            this.current = value;
        }

        @Override
        public boolean isValid(String value) {
            return value != null && this.parse(value).hasValue();
        }

        @Override
        public boolean isDefault() {
            ValidationResult<T> parsed = this.parse(this.current);
            return parsed.hasValue() && this.typed.type().equals(parsed.value(), this.typed.getDefault());
        }

        @Override
        public boolean isChanged() {
            return !this.current.equals(this.initial);
        }

        @Override
        public void restore() {
            this.current = this.getDefault();
        }

        @Override
        public void cleanCache() {
            this.initial = this.current;
        }

        @Override
        Object pending() {
            ValidationResult<T> parsed = this.parse(this.current);
            return parsed.hasValue() ? parsed.value() : this.typed.getStored();
        }

        private ValidationResult<T> parse(String text) {
            return this.typed.type().parse(text).then(this.typed::validate);
        }
    }
}
