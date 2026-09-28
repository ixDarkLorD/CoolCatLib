package net.ixdarklord.coolcatcore.api.config.annotation;

import net.ixdarklord.coolcatcore.api.config.RestartRequirement;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotations describing the fields of a {@link ConfigObject}'s class. Every non-static, non-transient field is an
 * entry; these add what the field's type and initial value can't say.
 */
public final class ConfigEntry {
    private ConfigEntry() {}

    /** Comment lines above the entry in the file; on the class, the file's header. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.TYPE})
    public @interface Comment {
        String[] value();
    }

    /** The key in the file, instead of the field's name. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Key {
        String value();
    }

    /** Keys the entry had before. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Aliases {
        String[] value();
    }

    /** A translation key for the entry's name. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Translation {
        String value();
    }

    /** The range of a number, or of a number list's elements. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Range {
        double min() default Double.NEGATIVE_INFINITY;

        double max() default Double.POSITIVE_INFINITY;
    }

    /** Shows a ranged number as a slider. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Slider {
    }

    /** An {@code int} field holding a color. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Color {
        boolean alpha() default false;
    }

    /** Text (or a string list's elements) limited in length. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface MaxLength {
        int value();
    }

    /** Text (or a string list's elements) matching a regular expression. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Pattern {
        String value();
    }

    /** A list's size bounds. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Size {
        int min() default 0;

        int max() default Integer.MAX_VALUE;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface RequiresRestart {
        RestartRequirement value() default RestartRequirement.GAME;
    }

    /** Kept on the server in a synced config; see {@code serverOnly()} on the entry builders. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface ServerOnly {
    }

    /** In a startup config, each side keeps its own value; see {@code localOnly()} on the entry builders. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface LocalOnly {
    }

    /** In a startup config, clients use the server's value while connected; see {@code useServerValue()}. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface UseServerValue {
    }

    /** Left out of the config screen. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Hidden {
    }

    /**
     * Greys the entry out while a boolean entry is off: its dotted path from the root ({@code "rendering.enabled"}),
     * declared before this one.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface EnabledWhen {
        String value();
    }
}
