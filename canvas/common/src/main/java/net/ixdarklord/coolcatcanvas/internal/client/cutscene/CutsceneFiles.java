package net.ixdarklord.coolcatcanvas.internal.client.cutscene;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.ixdarklord.coolcatcanvas.api.cutscene.Cutscene;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.ixdarklord.coolcatcore.api.platform.Platform;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

// The cutscenes saved from the in-game editor: one JSON file each in config/coolcatcanvas/cutscenes/, in the same
// format as a resource pack's, so a file can be copied straight to assets/<namespace>/cutscene/. They play by the id
// coolcatcanvas:saved/<name>.
public final class CutsceneFiles {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<String, Cutscene> SAVED = new TreeMap<>();
    private static boolean loaded;

    private CutsceneFiles() {}

    public static Path folder() {
        return Platform.getConfigFolder().resolve("coolcatcanvas").resolve("cutscenes");
    }

    public static Identifier id(String name) {
        return CoolCatCanvas.rl("saved/" + name);
    }

    /** Whether a name can be a file's: lower case letters, digits, underscores and dashes. */
    public static boolean isValidName(String name) {
        return !name.isEmpty() && name.length() <= 48 && name.chars().allMatch(c -> c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '-');
    }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        Path folder = folder();
        if (!Files.isDirectory(folder)) return;
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.toList()) {
                String fileName = file.getFileName().toString();
                if (!fileName.endsWith(".json")) continue;
                String name = fileName.substring(0, fileName.length() - 5);
                if (!isValidName(name)) continue;
                try (Reader reader = Files.newBufferedReader(file)) {
                    SAVED.put(name, parse(JsonParser.parseReader(reader)));
                } catch (IOException | RuntimeException e) {
                    CoolCatCanvas.LOGGER.warn("Couldn't read the saved cutscene {}: {}", file, e.getMessage());
                }
            }
        } catch (IOException e) {
            CoolCatCanvas.LOGGER.warn("Couldn't list the saved cutscenes in {}", folder, e);
        }
    }

    /** Reads a cutscene from JSON, as files and resource packs hold it. */
    static Cutscene parse(JsonElement json) {
        return Cutscene.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(JsonParseException::new);
    }

    public static synchronized List<String> names() {
        load();
        return new ArrayList<>(SAVED.keySet());
    }

    public static synchronized @Nullable Cutscene get(String name) {
        load();
        return SAVED.get(name);
    }

    static synchronized @Nullable Cutscene get(Identifier id) {
        if (!id.getNamespace().equals(CoolCatCanvas.MOD_ID) || !id.getPath().startsWith("saved/")) return null;
        return get(id.getPath().substring(6));
    }

    static synchronized List<Identifier> ids() {
        load();
        return SAVED.keySet().stream().map(CutsceneFiles::id).toList();
    }

    /**
     * Writes a cutscene to its file.
     *
     * @return the file
     * @throws IOException              if it can't be written
     * @throws IllegalArgumentException if the cutscene can't be written as JSON
     */
    public static synchronized Path save(String name, Cutscene cutscene) throws IOException {
        load();
        JsonElement json = Cutscene.CODEC.encodeStart(JsonOps.INSTANCE, cutscene).getOrThrow(IllegalArgumentException::new);
        Path file = folder().resolve(name + ".json");
        Files.createDirectories(file.getParent());
        try (Writer writer = Files.newBufferedWriter(file)) {
            GSON.toJson(json, writer);
        }
        SAVED.put(name, cutscene);
        return file;
    }
}
