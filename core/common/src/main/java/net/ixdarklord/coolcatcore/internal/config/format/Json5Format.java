package net.ixdarklord.coolcatcore.internal.config.format;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import net.ixdarklord.coolcatcore.api.config.format.ConfigDocument;
import net.ixdarklord.coolcatcore.api.config.format.ConfigFormat;
import net.ixdarklord.coolcatcore.api.config.format.ConfigFormatException;
import net.ixdarklord.coolcatcore.internal.config.ConfigJson;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

// JSON with // comments. Reading goes through Gson's lenient mode after dropping trailing commas, which it rejects.
public final class Json5Format implements ConfigFormat {
    private static final int INLINE_ARRAY_WIDTH = 72;

    @Override
    public String extension() {
        return "json5";
    }

    @Override
    public JsonObject read(String text) throws ConfigFormatException {
        JsonElement json;
        try {
            // A byte order mark (some Windows editors write one) would otherwise be read as garbage.
            json = ConfigJson.parseLenient(stripTrailingCommas(text.startsWith("\uFEFF") ? text.substring(1) : text));
        } catch (JsonParseException | IllegalStateException e) {
            throw new ConfigFormatException(e.getMessage() == null ? "Invalid JSON" : e.getMessage(), e);
        }
        if (!json.isJsonObject()) throw new ConfigFormatException("The file must hold a JSON object");
        return json.getAsJsonObject();
    }

    @Override
    public String write(ConfigDocument.Section document) {
        StringBuilder out = new StringBuilder();
        writeComment(out, document.comment(), 0);
        out.append('{');
        writeChildren(out, document.children(), 1);
        out.append("\n}\n");
        return out.toString();
    }

    private static void writeChildren(StringBuilder out, Map<String, ConfigDocument.Node> children, int depth) {
        Iterator<Map.Entry<String, ConfigDocument.Node>> iterator = children.entrySet().iterator();
        boolean first = true;
        while (iterator.hasNext()) {
            Map.Entry<String, ConfigDocument.Node> child = iterator.next();
            ConfigDocument.Node node = child.getValue();
            out.append('\n');
            // A blank line between commented or nested entries keeps them apart.
            if (!first && (!node.comment().isEmpty() || node instanceof ConfigDocument.Section)) out.append('\n');
            first = false;
            writeComment(out, node.comment(), depth);
            indent(out, depth).append(ConfigJson.compact(new JsonPrimitive(child.getKey()))).append(": ");
            if (node instanceof ConfigDocument.Section section) {
                out.append('{');
                writeChildren(out, section.children(), depth + 1);
                out.append('\n');
                indent(out, depth).append('}');
            } else if (node instanceof ConfigDocument.Entry entry) {
                writeValue(out, entry.value(), depth);
            }
            if (iterator.hasNext()) out.append(',');
        }
    }

    private static void writeValue(StringBuilder out, JsonElement value, int depth) {
        if (value instanceof JsonArray array) {
            String inline = ConfigJson.compact(array).replace(",", ", ");
            if (array.isEmpty() || (inline.length() <= INLINE_ARRAY_WIDTH && array.asList().stream().allMatch(JsonElement::isJsonPrimitive))) {
                out.append(inline);
                return;
            }
            out.append('[');
            for (int i = 0; i < array.size(); i++) {
                out.append('\n');
                indent(out, depth + 1);
                writeValue(out, array.get(i), depth + 1);
                if (i < array.size() - 1) out.append(',');
            }
            out.append('\n');
            indent(out, depth).append(']');
        } else if (value instanceof JsonObject object) {
            if (object.isEmpty()) {
                out.append("{}");
                return;
            }
            out.append('{');
            Iterator<Map.Entry<String, JsonElement>> iterator = object.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonElement> entry = iterator.next();
                out.append('\n');
                indent(out, depth + 1).append(ConfigJson.compact(new JsonPrimitive(entry.getKey()))).append(": ");
                writeValue(out, entry.getValue(), depth + 1);
                if (iterator.hasNext()) out.append(',');
            }
            out.append('\n');
            indent(out, depth).append('}');
        } else {
            out.append(ConfigJson.compact(value));
        }
    }

    private static void writeComment(StringBuilder out, List<String> comment, int depth) {
        for (String line : comment) {
            for (String part : line.split("\n", -1)) {
                indent(out, depth).append("// ").append(part).append('\n');
            }
        }
    }

    private static StringBuilder indent(StringBuilder out, int depth) {
        return out.append("\t".repeat(depth));
    }

    // Drops commas followed only by whitespace/comments and a closing bracket, outside strings and comments.
    static String stripTrailingCommas(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int length = text.length();
        int i = 0;
        while (i < length) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'') {
                int end = skipString(text, i);
                out.append(text, i, end);
                i = end;
            } else if (isCommentStart(text, i)) {
                int end = skipComment(text, i);
                out.append(text, i, end);
                i = end;
            } else if (c == ',') {
                int next = skipBlank(text, i + 1);
                if (next < length && (text.charAt(next) == '}' || text.charAt(next) == ']')) {
                    i++;
                    continue;
                }
                out.append(c);
                i++;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static int skipString(String text, int start) {
        char quote = text.charAt(start);
        int i = start + 1;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\') i += 2;
            else if (c == quote) return i + 1;
            else i++;
        }
        return text.length();
    }

    private static boolean isCommentStart(String text, int i) {
        char c = text.charAt(i);
        return c == '#' || (c == '/' && i + 1 < text.length() && (text.charAt(i + 1) == '/' || text.charAt(i + 1) == '*'));
    }

    private static int skipComment(String text, int start) {
        if (text.charAt(start) == '/' && text.charAt(start + 1) == '*') {
            int end = text.indexOf("*/", start + 2);
            return end < 0 ? text.length() : end + 2;
        }
        int end = text.indexOf('\n', start);
        return end < 0 ? text.length() : end;
    }

    private static int skipBlank(String text, int start) {
        int i = start;
        while (i < text.length()) {
            if (Character.isWhitespace(text.charAt(i))) i++;
            else if (isCommentStart(text, i)) i = skipComment(text, i);
            else break;
        }
        return i;
    }
}
