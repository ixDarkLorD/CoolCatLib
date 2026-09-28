package net.ixdarklord.coolcatcore.api.config.format;

import com.google.gson.JsonObject;

/**
 * A config file's syntax. Values reach a format already encoded as JSON trees by their codecs, and are read back as
 * JSON trees, so a format only translates syntax. {@link ConfigFormats} has the built-in ones.
 */
public interface ConfigFormat {
    /** The file extension, without the dot. */
    String extension();

    /**
     * Parses a file's text into nested objects.
     *
     * @throws ConfigFormatException when the text isn't valid in this format
     */
    JsonObject read(String text) throws ConfigFormatException;

    /** Writes a document, with its comments. */
    String write(ConfigDocument.Section document);
}
