package net.ixdarklord.coolcatcore.api.config.format;

import com.google.gson.JsonElement;

import java.util.List;
import java.util.Map;

/**
 * A config as a {@link ConfigFormat} writes it: nested sections of encoded values, each with its comment lines.
 */
public final class ConfigDocument {
    private ConfigDocument() {}

    public sealed interface Node permits Section, Entry {
        List<String> comment();
    }

    /** A group, or the whole file; children keep their declaration order. */
    public record Section(List<String> comment, Map<String, Node> children) implements Node {}

    /** A value, already encoded by its codec. */
    public record Entry(List<String> comment, JsonElement value) implements Node {}
}
