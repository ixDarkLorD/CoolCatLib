package net.ixdarklord.coolcatcore.internal.config;

import net.ixdarklord.coolcatcore.api.config.EntryBuilder;
import net.ixdarklord.coolcatcore.api.config.Config;
import net.ixdarklord.coolcatcore.api.config.ConfigBuilder;
import net.ixdarklord.coolcatcore.api.config.ConfigValue;
import net.ixdarklord.coolcatcore.api.config.annotation.ConfigEntry;
import net.ixdarklord.coolcatcore.api.config.type.ConfigType;
import net.ixdarklord.coolcatcore.api.config.type.ConfigTypes;
import net.ixdarklord.coolcatcore.api.config.type.NumberType;
import net.ixdarklord.coolcatcore.api.config.type.StringType;
import net.ixdarklord.coolcatcore.api.config.type.ValidationResult;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

// Turns an annotated object's fields into config entries, and keeps the fields in step with the values.
public final class ConfigObjectBinder {
    private final Object instance;
    private final List<Binding> bindings = new ArrayList<>();
    private final Map<String, ConfigValue<?>> byPath = new HashMap<>();
    private Config config;

    public ConfigObjectBinder(Object instance) {
        this.instance = instance;
    }

    public Config bind(ConfigBuilder builder) {
        ConfigEntry.Comment comment = this.instance.getClass().getAnnotation(ConfigEntry.Comment.class);
        if (comment != null) builder.comment(comment.value());
        try {
            this.bindFields(builder, this.instance, "");
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Can't access the fields of " + this.instance.getClass().getName(), e);
        }
        this.config = builder.build();
        return this.config;
    }

    /** Moves values assigned to the fields into the config. */
    public void pushFields() {
        Map<ConfigValueImpl<?>, Object> changes = new LinkedHashMap<>();
        for (Binding binding : this.bindings) {
            try {
                Object fieldValue = binding.field.get(binding.owner);
                if (fieldValue != null && binding.value.sameValue(fieldValue, binding.value.get())) continue;
                ValidationResult<?> result = fieldValue == null ? null : binding.value.validateUnchecked(fieldValue);
                if (result != null && result.hasValue()) {
                    changes.put(binding.value, result.value());
                } else {
                    CoolCatCore.LOGGER.warn("Config {}: field {} holds an invalid value ({}); putting it back",
                            this.config.id(), binding.value.path(), result == null ? "null" : result.messageString());
                    binding.field.set(binding.owner, binding.value.get());
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        ((ConfigImpl) this.config).applyChanges(changes);
    }

    private void bindFields(ConfigBuilder builder, Object owner, String prefix) throws IllegalAccessException {
        for (Field field : owner.getClass().getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()) continue;
            field.setAccessible(true);
            ConfigEntry.Key keyAnnotation = field.getAnnotation(ConfigEntry.Key.class);
            String key = keyAnnotation != null ? keyAnnotation.value() : field.getName();
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object initial = field.get(owner);
            Class<?> type = field.getType();

            ConfigType<?> configType = this.typeOf(field, type, field.getGenericType());
            if (configType != null) {
                if (initial == null) throw new IllegalArgumentException("Config field " + path + " has no default value");
                ConfigValueImpl<?> value = this.entry(builder, field, owner, key, path, configType, initial);
                this.byPath.put(path, value);
                this.bindings.add(new Binding(field, owner, value));
            } else if (!type.isPrimitive() && !type.isArray() && !type.getName().startsWith("java.")) {
                if (Modifier.isFinal(modifiers) && initial == null) throw new IllegalArgumentException("Config group " + path + " is null");
                Object group = initial;
                if (group == null) {
                    try {
                        var constructor = type.getDeclaredConstructor();
                        constructor.setAccessible(true);
                        group = constructor.newInstance();
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalArgumentException("Config group " + path + " is null and " + type.getName() + " has no no-argument constructor", e);
                    }
                    field.set(owner, group);
                }
                ConfigEntry.Comment comment = field.getAnnotation(ConfigEntry.Comment.class);
                builder.push(key, comment == null ? new String[0] : comment.value());
                this.bindFields(builder, group, path);
                builder.pop();
            } else {
                throw new IllegalArgumentException("Config field " + path + " has an unsupported type " + field.getGenericType());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> ConfigValueImpl<T> entry(ConfigBuilder builder, Field field, Object owner, String key, String path, ConfigType<T> type, Object initial) {
        EntryBuilder<T> entry = builder.value(key, type, (T) initial);
        ConfigEntry.Comment comment = field.getAnnotation(ConfigEntry.Comment.class);
        if (comment != null) entry.comment(comment.value());
        ConfigEntry.Translation translation = field.getAnnotation(ConfigEntry.Translation.class);
        if (translation != null) entry.translation(translation.value());
        ConfigEntry.Aliases aliases = field.getAnnotation(ConfigEntry.Aliases.class);
        if (aliases != null) entry.aliases(aliases.value());
        ConfigEntry.RequiresRestart restart = field.getAnnotation(ConfigEntry.RequiresRestart.class);
        if (restart != null) entry.requiresRestart(restart.value());
        if (field.isAnnotationPresent(ConfigEntry.ServerOnly.class)) entry.serverOnly();
        if (field.isAnnotationPresent(ConfigEntry.LocalOnly.class)) entry.localOnly();
        if (field.isAnnotationPresent(ConfigEntry.UseServerValue.class)) entry.useServerValue();
        if (field.isAnnotationPresent(ConfigEntry.Hidden.class)) entry.hidden();
        ConfigEntry.EnabledWhen enabledWhen = field.getAnnotation(ConfigEntry.EnabledWhen.class);
        if (enabledWhen != null) {
            ConfigValue<?> source = this.byPath.get(enabledWhen.value());
            if (source == null || !(source.getDefault() instanceof Boolean)) {
                throw new IllegalArgumentException("Config field " + path + " depends on " + enabledWhen.value() + ", which isn't a boolean declared before it");
            }
            entry.enabledWhen((ConfigValue<Boolean>) source);
        }
        entry.listener((oldValue, newValue) -> {
            try {
                field.set(owner, newValue);
            } catch (IllegalAccessException e) {
                CoolCatCore.LOGGER.error("Couldn't update config field {}", path, e);
            }
        });
        return (ConfigValueImpl<T>) entry.build();
    }

    // The field's config type, or null for a nested group.
    private ConfigType<?> typeOf(Field field, Class<?> type, Type genericType) {
        ConfigEntry.Range range = field.getAnnotation(ConfigEntry.Range.class);
        boolean slider = field.isAnnotationPresent(ConfigEntry.Slider.class);
        if (type == boolean.class || type == Boolean.class) return ConfigTypes.BOOLEAN;
        if (type == int.class || type == Integer.class) {
            ConfigEntry.Color color = field.getAnnotation(ConfigEntry.Color.class);
            if (color != null) return color.alpha() ? ConfigTypes.COLOR_ALPHA : ConfigTypes.COLOR;
            return number(ConfigTypes.INT, range, slider, d -> (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, d)));
        }
        if (type == long.class || type == Long.class) return number(ConfigTypes.LONG, range, slider, Double::longValue);
        if (type == float.class || type == Float.class) return number(ConfigTypes.FLOAT, range, slider, d -> (float) Math.max(-Float.MAX_VALUE, Math.min(Float.MAX_VALUE, d)));
        if (type == double.class || type == Double.class) return number(ConfigTypes.DOUBLE, range, slider, d -> Math.max(-Double.MAX_VALUE, Math.min(Double.MAX_VALUE, d)));
        if (type == String.class) {
            StringType string = ConfigTypes.STRING;
            ConfigEntry.MaxLength maxLength = field.getAnnotation(ConfigEntry.MaxLength.class);
            if (maxLength != null) string = string.withMaxLength(maxLength.value());
            ConfigEntry.Pattern pattern = field.getAnnotation(ConfigEntry.Pattern.class);
            if (pattern != null) string = string.withPattern(pattern.value());
            return string;
        }
        if (type == ResourceLocation.class) return ConfigTypes.IDENTIFIER;
        if (type.isEnum()) return enumType(type);
        if (List.class.isAssignableFrom(type)) {
            if (!(genericType instanceof ParameterizedType parameterized) || !(parameterized.getActualTypeArguments()[0] instanceof Class<?> element)) {
                throw new IllegalArgumentException("Config list field " + field.getName() + " needs a concrete element type");
            }
            ConfigType<?> elementType = this.typeOf(field, element, element);
            if (elementType == null || List.class.isAssignableFrom(element)) {
                throw new IllegalArgumentException("Config list field " + field.getName() + " has an unsupported element type " + element.getName());
            }
            ConfigEntry.Size size = field.getAnnotation(ConfigEntry.Size.class);
            return size == null ? ConfigTypes.listOf(elementType) : ConfigTypes.listOf(elementType, size.min(), size.max());
        }
        return null;
    }

    private static <N extends Number & Comparable<N>> NumberType<N> number(NumberType<N> type, ConfigEntry.Range range, boolean slider, Function<Double, N> convert) {
        if (range != null) {
            N min = Double.isInfinite(range.min()) ? type.kind().lowest() : convert.apply(range.min());
            N max = Double.isInfinite(range.max()) ? type.kind().highest() : convert.apply(range.max());
            type = type.withRange(min, max);
        }
        return slider ? type.withSlider(true) : type;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ConfigType<?> enumType(Class<?> type) {
        return ConfigTypes.enumOf((Class) type);
    }

    private record Binding(Field field, Object owner, ConfigValueImpl<?> value) {}
}
