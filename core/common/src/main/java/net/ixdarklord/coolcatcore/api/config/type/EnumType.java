package net.ixdarklord.coolcatcore.api.config.type;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * One of an enum's constants, stored by name (read case-insensitively). Give constants a name for the screen by
 * implementing {@link Displayable}; otherwise {@code FANCY_LEAVES} shows as "Fancy Leaves".
 */
public final class EnumType<E extends Enum<E>> implements ConfigType<E> {
    private final Class<E> enumClass;
    private final List<E> constants;
    private final Codec<E> codec;

    EnumType(Class<E> enumClass) {
        this.enumClass = enumClass;
        this.constants = List.of(enumClass.getEnumConstants());
        this.codec = Codec.STRING.comapFlatMap(name -> {
            E constant = this.byName(name);
            return constant != null ? DataResult.success(constant) : DataResult.error(() -> "Unknown value " + name + "; expected one of " + this.names());
        }, Enum::name);
    }

    public Class<E> enumClass() {
        return this.enumClass;
    }

    public List<E> constants() {
        return this.constants;
    }

    /** The constant after (or before) this one, wrapping around. */
    public E cycle(E current, boolean backwards) {
        int index = this.constants.indexOf(current) + (backwards ? -1 : 1);
        return this.constants.get(Math.floorMod(index, this.constants.size()));
    }

    public Component displayName(E constant) {
        return constant instanceof Displayable displayable ? displayable.displayName() : Component.literal(prettify(constant.name()));
    }

    @Override
    public Codec<E> codec() {
        return this.codec;
    }

    @Override
    public ValidationResult<E> validate(E value) {
        return ValidationResult.ok(value);
    }

    @Override
    public ValidationResult<E> parse(String text) {
        E constant = this.byName(text.trim());
        return constant != null ? ValidationResult.ok(constant)
                : ValidationResult.error(Component.translatableWithFallback("config.coolcatcore.error.enum", "Must be one of %s", this.names()));
    }

    @Override
    public String format(E value) {
        return value.name();
    }

    @Override
    public List<Component> describe() {
        return List.of(Component.translatableWithFallback("config.coolcatcore.info.allowed", "Allowed values: %s", this.names()));
    }

    @Override
    public List<String> suggestions() {
        return this.constants.stream().map(Enum::name).toList();
    }

    private E byName(String name) {
        for (E constant : this.constants) {
            if (constant.name().equalsIgnoreCase(name)) return constant;
        }
        return null;
    }

    private String names() {
        return this.constants.stream().map(Enum::name).collect(Collectors.joining(", "));
    }

    static String prettify(String name) {
        return Arrays.stream(name.toLowerCase(Locale.ROOT).split("_"))
                .filter(word -> !word.isEmpty())
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    /** An enum constant with its own name in the config screen. */
    public interface Displayable {
        Component displayName();
    }
}
