package net.ixdarklord.coolcatcore.api.config.format;

import net.ixdarklord.coolcatcore.internal.config.format.Json5Format;
import net.ixdarklord.coolcatcore.internal.config.format.TomlFormat;

/**
 * The built-in file formats.
 */
public final class ConfigFormats {
    /**
     * JSON with comments ({@code .json5}). Reading is forgiving: comments, trailing commas, unquoted
     * keys and single-quoted strings are accepted.
     */
    public static final ConfigFormat JSON5 = new Json5Format();
    /** TOML ({@code .toml}), the default; groups become tables. */
    public static final ConfigFormat TOML = new TomlFormat();

    private ConfigFormats() {}
}
