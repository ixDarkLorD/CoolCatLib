package net.ixdarklord.coolcatcore.internal.config.format;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.ixdarklord.coolcatcore.api.config.format.ConfigDocument;
import net.ixdarklord.coolcatcore.api.config.format.ConfigFormat;
import net.ixdarklord.coolcatcore.api.config.format.ConfigFormatException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

// TOML without a library (NightConfig only ships with NeoForge). Groups become [tables], objects inside values
// become inline tables. The reader covers TOML 1.0 except that dates are kept as strings.
public final class TomlFormat implements ConfigFormat {
    private static final Pattern BARE_KEY = Pattern.compile("[A-Za-z0-9_-]+");
    private static final int INLINE_ARRAY_WIDTH = 72;

    @Override
    public String extension() {
        return "toml";
    }

    @Override
    public JsonObject read(String text) throws ConfigFormatException {
        return new Parser(text).parse();
    }

    @Override
    public String write(ConfigDocument.Section document) {
        StringBuilder out = new StringBuilder();
        writeComment(out, document.comment(), "");
        if (!document.comment().isEmpty()) out.append('\n');
        writeSection(out, document, List.of());
        return out.toString();
    }

    // A table's own values come before its sub-tables, as TOML requires.
    private static void writeSection(StringBuilder out, ConfigDocument.Section section, List<String> path) {
        boolean first = true;
        for (Map.Entry<String, ConfigDocument.Node> child : section.children().entrySet()) {
            if (!(child.getValue() instanceof ConfigDocument.Entry entry) || entry.value().isJsonNull()) continue;
            if (!first && !entry.comment().isEmpty()) out.append('\n');
            first = false;
            writeComment(out, entry.comment(), "");
            out.append(key(child.getKey())).append(" = ");
            writeValue(out, entry.value(), "");
            out.append('\n');
        }
        for (Map.Entry<String, ConfigDocument.Node> child : section.children().entrySet()) {
            if (!(child.getValue() instanceof ConfigDocument.Section subsection)) continue;
            List<String> subpath = new ArrayList<>(path);
            subpath.add(child.getKey());
            out.append('\n');
            writeComment(out, subsection.comment(), "");
            out.append('[').append(String.join(".", subpath.stream().map(TomlFormat::key).toList())).append("]\n");
            writeSection(out, subsection, subpath);
        }
    }

    private static void writeValue(StringBuilder out, JsonElement value, String indent) {
        if (value instanceof JsonArray array) {
            StringBuilder inline = new StringBuilder("[");
            for (int i = 0; i < array.size(); i++) {
                if (i > 0) inline.append(", ");
                writeValue(inline, array.get(i), indent);
            }
            inline.append(']');
            if (inline.length() <= INLINE_ARRAY_WIDTH || inline.indexOf("\n") >= 0) {
                out.append(inline);
                return;
            }
            out.append("[\n");
            for (JsonElement element : array) {
                out.append(indent).append('\t');
                writeValue(out, element, indent + "\t");
                out.append(",\n");
            }
            out.append(indent).append(']');
        } else if (value instanceof JsonObject object) {
            out.append('{');
            Iterator<Map.Entry<String, JsonElement>> iterator = object.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonElement> entry = iterator.next();
                out.append(' ').append(key(entry.getKey())).append(" = ");
                writeValue(out, entry.getValue(), indent);
                out.append(iterator.hasNext() ? "," : " ");
            }
            out.append('}');
        } else if (value instanceof JsonPrimitive primitive) {
            if (primitive.isString()) {
                out.append(quote(primitive.getAsString()));
            } else if (primitive.isNumber()) {
                double number = primitive.getAsDouble();
                if (Double.isNaN(number)) out.append("nan");
                else if (Double.isInfinite(number)) out.append(number > 0 ? "inf" : "-inf");
                else out.append(primitive.getAsNumber().toString());
            } else {
                out.append(primitive.getAsBoolean());
            }
        } else {
            out.append("\"\"");
        }
    }

    private static void writeComment(StringBuilder out, List<String> comment, String indent) {
        for (String line : comment) {
            for (String part : line.split("\n", -1)) {
                out.append(indent).append("# ").append(part).append('\n');
            }
        }
    }

    private static String key(String key) {
        return BARE_KEY.matcher(key).matches() ? key : quote(key);
    }

    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20 || c == 0x7F) out.append(String.format("\\u%04X", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }

    private static final class Parser {
        private final String text;
        private final JsonObject root = new JsonObject();
        private int pos;

        Parser(String text) {
            // A byte order mark would otherwise be read as part of the first key.
            this.text = text.startsWith("﻿") ? text.substring(1) : text;
        }

        JsonObject parse() throws ConfigFormatException {
            JsonObject table = this.root;
            while (true) {
                this.skipBlankLines();
                if (this.pos >= this.text.length()) return this.root;
                if (this.peek() == '[') {
                    boolean arrayOfTables = this.text.startsWith("[[", this.pos);
                    this.pos += arrayOfTables ? 2 : 1;
                    List<String> path = this.readKey();
                    this.expect(arrayOfTables ? "]]" : "]");
                    table = arrayOfTables ? this.appendTable(path) : this.table(this.root, path, true);
                } else {
                    List<String> path = this.readKey();
                    this.skipSpaces();
                    this.expect("=");
                    this.skipSpaces();
                    JsonElement value = this.readValue();
                    JsonObject target = this.table(table, path.subList(0, path.size() - 1), false);
                    String key = path.getLast();
                    if (target.has(key)) throw this.error("Duplicate key " + String.join(".", path));
                    target.add(key, value);
                }
                this.endOfLine();
            }
        }

        // Walks (creating) nested tables; the last element of an array of tables stands for the array.
        private JsonObject table(JsonObject from, List<String> path, boolean header) throws ConfigFormatException {
            JsonObject table = from;
            for (String key : path) {
                JsonElement next = table.get(key);
                if (next == null) {
                    next = new JsonObject();
                    table.add(key, next);
                } else if (next instanceof JsonArray array && !array.isEmpty() && array.get(array.size() - 1) instanceof JsonObject last && header) {
                    next = last;
                } else if (!next.isJsonObject()) {
                    throw this.error("Key " + key + " is already a value, not a table");
                }
                table = next.getAsJsonObject();
            }
            return table;
        }

        private JsonObject appendTable(List<String> path) throws ConfigFormatException {
            JsonObject parent = this.table(this.root, path.subList(0, path.size() - 1), true);
            String key = path.getLast();
            JsonElement existing = parent.get(key);
            JsonArray array;
            if (existing == null) {
                array = new JsonArray();
                parent.add(key, array);
            } else if (existing instanceof JsonArray found) {
                array = found;
            } else {
                throw this.error("Key " + key + " is not an array of tables");
            }
            JsonObject table = new JsonObject();
            array.add(table);
            return table;
        }

        private List<String> readKey() throws ConfigFormatException {
            List<String> parts = new ArrayList<>();
            while (true) {
                this.skipSpaces();
                char c = this.peek();
                if (c == '"') parts.add(this.readBasicString());
                else if (c == '\'') parts.add(this.readLiteralString());
                else {
                    int start = this.pos;
                    while (this.pos < this.text.length() && isBareKeyChar(this.text.charAt(this.pos))) this.pos++;
                    if (start == this.pos) throw this.error("Expected a key");
                    parts.add(this.text.substring(start, this.pos));
                }
                this.skipSpaces();
                if (this.peek() != '.') return parts;
                this.pos++;
            }
        }

        private JsonElement readValue() throws ConfigFormatException {
            char c = this.peek();
            if (this.text.startsWith("\"\"\"", this.pos)) return new JsonPrimitive(this.readMultilineBasicString());
            if (this.text.startsWith("'''", this.pos)) return new JsonPrimitive(this.readMultilineLiteralString());
            if (c == '"') return new JsonPrimitive(this.readBasicString());
            if (c == '\'') return new JsonPrimitive(this.readLiteralString());
            if (c == '[') return this.readArray();
            if (c == '{') return this.readInlineTable();
            if (this.text.startsWith("true", this.pos) && !this.continuesToken(this.pos + 4)) {
                this.pos += 4;
                return new JsonPrimitive(true);
            }
            if (this.text.startsWith("false", this.pos) && !this.continuesToken(this.pos + 5)) {
                this.pos += 5;
                return new JsonPrimitive(false);
            }
            return this.readScalar();
        }

        private JsonArray readArray() throws ConfigFormatException {
            this.expect("[");
            JsonArray array = new JsonArray();
            while (true) {
                this.skipBlankLines();
                if (this.peek() == ']') {
                    this.pos++;
                    return array;
                }
                array.add(this.readValue());
                this.skipBlankLines();
                if (this.peek() == ',') {
                    this.pos++;
                } else if (this.peek() != ']') {
                    throw this.error("Expected , or ] in an array");
                }
            }
        }

        private JsonObject readInlineTable() throws ConfigFormatException {
            this.expect("{");
            JsonObject table = new JsonObject();
            this.skipSpaces();
            if (this.peek() == '}') {
                this.pos++;
                return table;
            }
            while (true) {
                List<String> path = this.readKey();
                this.skipSpaces();
                this.expect("=");
                this.skipSpaces();
                JsonObject target = this.table(table, path.subList(0, path.size() - 1), false);
                target.add(path.getLast(), this.readValue());
                this.skipSpaces();
                char c = this.peek();
                this.pos++;
                if (c == '}') return table;
                if (c != ',') throw this.error("Expected , or } in an inline table");
            }
        }

        // Numbers; anything else unquoted (dates, times) is kept as a string.
        private JsonElement readScalar() throws ConfigFormatException {
            int start = this.pos;
            int end = start;
            while (end < this.text.length() && ",]}#\r\n".indexOf(this.text.charAt(end)) < 0) end++;
            while (end > start && Character.isWhitespace(this.text.charAt(end - 1))) end--;
            this.pos = end;
            String token = this.text.substring(start, end);
            if (token.isEmpty()) throw this.error("Expected a value");
            String number = token.replace("_", "");
            switch (number) {
                case "inf", "+inf" -> {
                    return new JsonPrimitive(Double.POSITIVE_INFINITY);
                }
                case "-inf" -> {
                    return new JsonPrimitive(Double.NEGATIVE_INFINITY);
                }
                case "nan", "+nan", "-nan" -> {
                    return new JsonPrimitive(Double.NaN);
                }
                default -> {
                }
            }
            try {
                if (number.startsWith("0x")) return new JsonPrimitive(Long.parseLong(number.substring(2), 16));
                if (number.startsWith("0o")) return new JsonPrimitive(Long.parseLong(number.substring(2), 8));
                if (number.startsWith("0b")) return new JsonPrimitive(Long.parseLong(number.substring(2), 2));
                if (number.matches("[+-]?\\d+")) return new JsonPrimitive(Long.parseLong(number));
                if (number.matches("[+-]?\\d+(\\.\\d+)?([eE][+-]?\\d+)?")) return new JsonPrimitive(Double.parseDouble(number));
            } catch (NumberFormatException e) {
                throw this.error("Invalid number " + token);
            }
            if (token.matches("\\d{4}-\\d{2}-\\d{2}.*|\\d{2}:\\d{2}.*")) return new JsonPrimitive(token);
            throw this.error("Unexpected value " + token);
        }

        private String readBasicString() throws ConfigFormatException {
            this.expect("\"");
            StringBuilder out = new StringBuilder();
            while (true) {
                if (this.pos >= this.text.length()) throw this.error("Unterminated string");
                char c = this.text.charAt(this.pos++);
                if (c == '"') return out.toString();
                if (c == '\n') throw this.error("Unterminated string");
                if (c == '\\') this.readEscape(out);
                else out.append(c);
            }
        }

        private String readMultilineBasicString() throws ConfigFormatException {
            this.pos += 3;
            this.skipNewline();
            StringBuilder out = new StringBuilder();
            while (true) {
                if (this.pos >= this.text.length()) throw this.error("Unterminated string");
                if (this.text.startsWith("\"\"\"", this.pos)) {
                    this.pos += 3;
                    // Up to two quotes may directly precede the closing delimiter.
                    for (int quotes = 0; quotes < 2 && this.peek() == '"'; quotes++) {
                        out.append('"');
                        this.pos++;
                    }
                    return out.toString();
                }
                char c = this.text.charAt(this.pos++);
                if (c == '\\') {
                    int save = this.pos;
                    while (this.pos < this.text.length() && (this.text.charAt(this.pos) == ' ' || this.text.charAt(this.pos) == '\t')) this.pos++;
                    if (this.peek() == '\n' || this.peek() == '\r') {
                        // A line-ending backslash trims the line break and the next line's indentation.
                        while (this.pos < this.text.length() && Character.isWhitespace(this.text.charAt(this.pos))) this.pos++;
                    } else {
                        this.pos = save;
                        this.readEscape(out);
                    }
                } else {
                    out.append(c);
                }
            }
        }

        private String readLiteralString() throws ConfigFormatException {
            this.expect("'");
            int end = this.text.indexOf('\'', this.pos);
            int newline = this.text.indexOf('\n', this.pos);
            if (end < 0 || (newline >= 0 && newline < end)) throw this.error("Unterminated string");
            String value = this.text.substring(this.pos, end);
            this.pos = end + 1;
            return value;
        }

        private String readMultilineLiteralString() throws ConfigFormatException {
            this.pos += 3;
            this.skipNewline();
            int end = this.text.indexOf("'''", this.pos);
            if (end < 0) throw this.error("Unterminated string");
            while (end + 3 < this.text.length() && this.text.charAt(end + 3) == '\'') end++;
            String value = this.text.substring(this.pos, end);
            this.pos = end + 3;
            return value;
        }

        private void readEscape(StringBuilder out) throws ConfigFormatException {
            if (this.pos >= this.text.length()) throw this.error("Unterminated escape");
            char c = this.text.charAt(this.pos++);
            switch (c) {
                case 'b' -> out.append('\b');
                case 't' -> out.append('\t');
                case 'n' -> out.append('\n');
                case 'f' -> out.append('\f');
                case 'r' -> out.append('\r');
                case 'e' -> out.append('\u001B');
                case '"' -> out.append('"');
                case '\\' -> out.append('\\');
                case 'u', 'U' -> {
                    int digits = c == 'u' ? 4 : 8;
                    if (this.pos + digits > this.text.length()) throw this.error("Invalid unicode escape");
                    try {
                        out.appendCodePoint(Integer.parseInt(this.text.substring(this.pos, this.pos + digits), 16));
                    } catch (IllegalArgumentException e) {
                        throw this.error("Invalid unicode escape");
                    }
                    this.pos += digits;
                }
                default -> throw this.error("Invalid escape \\" + c);
            }
        }

        private void skipNewline() {
            if (this.text.startsWith("\r\n", this.pos)) this.pos += 2;
            else if (this.peek() == '\n') this.pos++;
        }

        private void skipSpaces() {
            while (this.pos < this.text.length() && (this.text.charAt(this.pos) == ' ' || this.text.charAt(this.pos) == '\t')) this.pos++;
        }

        // Whitespace, line breaks and comments.
        private void skipBlankLines() {
            while (this.pos < this.text.length()) {
                char c = this.text.charAt(this.pos);
                if (Character.isWhitespace(c)) {
                    this.pos++;
                } else if (c == '#') {
                    while (this.pos < this.text.length() && this.text.charAt(this.pos) != '\n') this.pos++;
                } else {
                    return;
                }
            }
        }

        private void endOfLine() throws ConfigFormatException {
            this.skipSpaces();
            if (this.peek() == '#') {
                while (this.pos < this.text.length() && this.text.charAt(this.pos) != '\n') this.pos++;
            }
            if (this.pos >= this.text.length()) return;
            if (this.peek() == '\r') this.pos++;
            if (this.peek() != '\n') throw this.error("Expected the end of the line");
            this.pos++;
        }

        private void expect(String token) throws ConfigFormatException {
            if (!this.text.startsWith(token, this.pos)) throw this.error("Expected " + token);
            this.pos += token.length();
        }

        private boolean continuesToken(int index) {
            return index < this.text.length() && isBareKeyChar(this.text.charAt(index));
        }

        private char peek() {
            return this.pos < this.text.length() ? this.text.charAt(this.pos) : '\0';
        }

        private ConfigFormatException error(String message) {
            int line = 1;
            int column = 1;
            for (int i = 0; i < Math.min(this.pos, this.text.length()); i++) {
                if (this.text.charAt(i) == '\n') {
                    line++;
                    column = 1;
                } else {
                    column++;
                }
            }
            return new ConfigFormatException(message + " (line " + line + ", column " + column + ")");
        }

        private static boolean isBareKeyChar(char c) {
            return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
        }
    }
}
