package net.ixdarklord.coolcatlib.api.util;

import java.util.function.Function;

/**
 * A utility class for parsing strings into values of type T and converting values of type T into strings.
 *
 * @param <T> The type of value to parse and convert.
 */
public class ValueConverter<T> {
    private final Function<String, T> parser;
    private final Function<T, String> toStringConverter;

    /**
     * A ValueConverter for String values.
     */
    public static final ValueConverter<String> STRING = new ValueConverter<>(s -> s, s -> s);

    /**
     * A ValueConverter for Integer values.
     */
    public static final ValueConverter<Integer> INTEGER = new ValueConverter<>(Integer::parseInt, Object::toString);

    /**
     * A ValueConverter for Double values.
     */
    public static final ValueConverter<Double> DOUBLE = new ValueConverter<>(Double::parseDouble, Object::toString);

    /**
     * A ValueConverter for Boolean values.
     */
    public static final ValueConverter<Boolean> BOOLEAN = new ValueConverter<>(Boolean::parseBoolean, Object::toString);

    /**
     * A ValueConverter for Enum values.
     * This method creates a ValueConverter for a specific enum class.
     *
     * @param enumClass The class of the enum.
     * @return A ValueConverter for the specified enum type.
     */
    public static <E extends Enum<E>> ValueConverter<E> forEnum(Class<E> enumClass) {
        return new ValueConverter<>(s -> Enum.valueOf(enumClass, s), Enum::name);
    }

    /**
     * Private constructor for ValueConverter.
     *
     * @param parser             A function to parse a string into the type T.
     * @param toStringConverter  A function to convert the type T to a string.
     */
    private ValueConverter(Function<String, T> parser, Function<T, String> toStringConverter) {
        this.parser = parser;
        this.toStringConverter = toStringConverter;
    }

    /**
     * Static factory method to create a ValueConverter instance.
     *
     * @param parser             A function to parse a string into the type T.
     * @param toStringConverter  A function to convert the type T to a string.
     * @return A new ValueConverter instance.
     */
    public static <T> ValueConverter<T> of(Function<String, T> parser, Function<T, String> toStringConverter) {
        return new ValueConverter<>(parser, toStringConverter);
    }

    /**
     * Parses a string into the type T.
     *
     * @param input The input string to parse.
     * @return The parsed value of type T.
     * @throws IllegalArgumentException If the input cannot be parsed.
     */
    public T parse(String input) {
        try {
            return parser.apply(input);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid value: " + input, e);
        }
    }

    /**
     * Converts a value of type T to a string.
     *
     * @param value The value to convert.
     * @return The string representation of the value.
     */
    public String toString(T value) {
        return toStringConverter.apply(value);
    }
}