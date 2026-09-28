package net.ixdarklord.coolcatcore.internal.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.ixdarklord.coolcatcore.api.config.Config;
import net.ixdarklord.coolcatcore.api.config.ConfigEvents;
import net.ixdarklord.coolcatcore.api.config.ConfigMigration;
import net.ixdarklord.coolcatcore.api.config.ConfigNode;
import net.ixdarklord.coolcatcore.api.config.ConfigPreset;
import net.ixdarklord.coolcatcore.api.config.ConfigScope;
import net.ixdarklord.coolcatcore.api.config.ConfigTheme;
import net.ixdarklord.coolcatcore.api.config.ConfigValue;
import net.ixdarklord.coolcatcore.api.config.RestartRequirement;
import net.ixdarklord.coolcatcore.api.config.StartupSync;
import net.ixdarklord.coolcatcore.api.config.format.ConfigDocument;
import net.ixdarklord.coolcatcore.api.config.format.ConfigFormat;
import net.ixdarklord.coolcatcore.api.config.format.ConfigFormatException;
import net.ixdarklord.coolcatcore.api.config.format.ConfigFormats;
import net.ixdarklord.coolcatcore.api.config.type.ValidationResult;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ConfigImpl implements Config {
    public static final String VERSION_KEY = "$version";
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int MAX_DEFAULT_COMMENT_LENGTH = 80;

    private final ResourceLocation id;
    private final ConfigScope scope;
    private final ConfigFormat format;
    private final ConfigGroupImpl root;
    private final List<String> comment;
    private final int version;
    private final Map<Integer, ConfigMigration> migrations;
    private final int editPermission;
    private final boolean hotReload;
    private final String fileName;
    private final Map<String, ConfigValueImpl<?>> values = new LinkedHashMap<>();
    private final Map<String, ConfigPreset> presets;
    private final List<ChangeListener> listeners = new CopyOnWriteArrayList<>();
    private final Object ioLock = new Object();
    private final @Nullable ConfigTheme theme;

    private volatile @Nullable Path file;
    private volatile boolean loaded;
    private volatile boolean remote;
    // A startup config stops changing its live values after the first load; later changes wait here for a restart.
    private volatile boolean frozen;
    private final Map<ConfigValueImpl<?>, Object> nextStart = new ConcurrentHashMap<>();
    // Hash of the text last read or written, so the file watcher can tell our own writes from edits.
    private volatile int knownContentHash;

    public ConfigImpl(ResourceLocation id, ConfigScope scope, ConfigFormat format, ConfigGroupImpl root, List<String> comment, int version,
                      Map<Integer, ConfigMigration> migrations, int editPermission, boolean hotReload, String fileName,
                      Map<String, ConfigPreset> presets, @Nullable ConfigTheme theme) {
        this.theme = theme;
        this.id = id;
        this.scope = scope;
        this.format = format;
        this.root = root;
        this.comment = List.copyOf(comment);
        this.version = version;
        this.migrations = Map.copyOf(migrations);
        this.editPermission = editPermission;
        this.hotReload = hotReload;
        this.fileName = fileName;
        this.presets = Collections.unmodifiableMap(new LinkedHashMap<>(presets));
        root.attach(this);
        root.values().forEach(value -> this.values.put(value.path(), (ConfigValueImpl<?>) value));
    }

    // --- Identity ---

    @Override
    public ResourceLocation id() {
        return this.id;
    }

    @Override
    public ConfigScope scope() {
        return this.scope;
    }

    @Override
    public ConfigFormat format() {
        return this.format;
    }

    @Override
    public ConfigGroupImpl root() {
        return this.root;
    }

    @Override
    public Component title() {
        return Component.translatableWithFallback("config." + this.modId() + "." + this.name() + ".title",
                ConfigNodeImpl.prettify(this.modId()) + " " + ConfigNodeImpl.prettify(this.name()));
    }

    @Override
    public ConfigTheme theme() {
        return this.theme != null ? this.theme : ConfigTheme.forMod(this.modId());
    }

    @Override
    public List<String> comment() {
        return this.comment;
    }

    @Override
    public int version() {
        return this.version;
    }

    @Override
    public int editPermission() {
        return this.editPermission;
    }

    public boolean hotReload() {
        return this.hotReload;
    }

    /** The file name with its extension, relative to the folder the scope stores configs in. */
    public String fileName() {
        return this.fileName + "." + this.format.extension();
    }

    @Override
    public @Nullable Path filePath() {
        return this.file;
    }

    @Override
    public boolean isLoaded() {
        return this.loaded;
    }

    @Override
    public boolean isRemote() {
        return this.remote;
    }

    public void setRemote(boolean remote) {
        this.remote = remote;
    }

    @Override
    public Optional<ConfigValue<?>> find(String path) {
        return Optional.ofNullable(this.values.get(path));
    }

    public @Nullable ConfigValueImpl<?> value(String path) {
        return this.values.get(path);
    }

    public Iterable<ConfigValueImpl<?>> allValues() {
        return this.values.values();
    }

    @Override
    public Map<String, ConfigPreset> presets() {
        return this.presets;
    }

    // --- Changes ---

    @Override
    public void applyPreset(ConfigPreset preset) {
        Map<ConfigValueImpl<?>, Object> changes = new LinkedHashMap<>();
        preset.values().forEach((value, presetValue) -> changes.put((ConfigValueImpl<?>) value, presetValue));
        this.applyChanges(changes);
    }

    @Override
    public void resetAll() {
        Map<ConfigValueImpl<?>, Object> changes = new LinkedHashMap<>();
        this.values.values().forEach(value -> changes.put(value, value.getDefault()));
        this.applyChanges(changes);
    }

    /**
     * Stores already-validated values at once, then notifies value listeners, config listeners and
     * {@link ConfigEvents#CHANGED} of the ones that actually changed. Once a startup config is frozen, the values
     * are only stored for the next start instead.
     */
    public Set<ConfigValue<?>> applyChanges(Map<? extends ConfigValueImpl<?>, ?> changes) {
        return this.frozen ? this.storeForNextStart(changes) : this.applyLive(changes);
    }

    /** Changes the live values even when frozen: the server's values arriving, or the client's own coming back. */
    public Set<ConfigValue<?>> applyLive(Map<? extends ConfigValueImpl<?>, ?> changes) {
        record Change(ConfigValueImpl<?> value, Object oldValue, Object newValue) {}
        List<Change> applied = new ArrayList<>();
        synchronized (this) {
            changes.forEach((value, newValue) -> {
                Object old = value.get();
                if (!value.sameValue(old, newValue)) {
                    value.swap(newValue);
                    applied.add(new Change(value, old, newValue));
                }
            });
        }
        if (applied.isEmpty()) return Set.of();

        Set<ConfigValue<?>> changed = new LinkedHashSet<>();
        for (Change change : applied) {
            change.value.fireListeners(change.oldValue, change.newValue);
            ConfigEvents.VALUE_CHANGED.invoker().onValueChanged(this, change.value, change.oldValue, change.newValue);
            changed.add(change.value);
        }
        Set<ConfigValue<?>> view = Collections.unmodifiableSet(changed);
        for (ChangeListener listener : this.listeners) {
            try {
                listener.onChanged(this, view);
            } catch (RuntimeException e) {
                CoolCatCore.LOGGER.error("A listener of config {} failed", this.id, e);
            }
        }
        ConfigEvents.CHANGED.invoker().onChanged(this, view);
        ConfigManager.onChanged(this);
        return view;
    }

    /** The strictest restart any of these values needs. */
    public static RestartRequirement restartFor(Iterable<? extends ConfigValue<?>> values) {
        RestartRequirement restart = RestartRequirement.NONE;
        for (ConfigValue<?> value : values) restart = restart.max(value.restartRequirement());
        return restart;
    }

    @Override
    public void addListener(ChangeListener listener) {
        this.listeners.add(listener);
    }

    @Override
    public boolean removeListener(ChangeListener listener) {
        return this.listeners.remove(listener);
    }

    // --- Files ---

    void bindFile(@Nullable Path file) {
        this.file = file;
        this.knownContentHash = 0;
        if (file == null) this.loaded = false;
    }

    /** First load: a missing file is written with defaults, an unreadable one is backed up and replaced. */
    void load() {
        this.read(true);
    }

    @Override
    public void reload() {
        this.read(false);
    }

    private void read(boolean initial) {
        Path file = this.file;
        if (file == null || this.remote) return;
        String text;
        try {
            if (Files.notExists(file)) {
                if (this.convertFromOtherFormat(file, initial)) return;
                if (!this.copyDefaultConfig(file)) {
                    if (!initial) this.resetAll();
                    this.loaded = true;
                    this.save();
                    this.fireLoaded(initial);
                    return;
                }
            }
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            CoolCatCore.LOGGER.error("Couldn't read config {} from {}", this.id, file, e);
            return;
        }
        this.readText(text, initial);
    }

    // A config whose format changed (the default becoming TOML, or a mod switching) keeps its values: the file in the
    // other built-in format is read, written in this one, and kept beside it as a .bak.
    private boolean convertFromOtherFormat(Path file, boolean initial) throws IOException {
        String name = file.getFileName().toString();
        String base = name.substring(0, name.length() - this.format.extension().length() - 1);
        for (ConfigFormat other : List.of(ConfigFormats.TOML, ConfigFormats.JSON5)) {
            if (other.extension().equals(this.format.extension())) continue;
            Path old = file.resolveSibling(base + "." + other.extension());
            if (Files.notExists(old)) continue;
            JsonObject root;
            try {
                root = other.read(Files.readString(old, StandardCharsets.UTF_8));
            } catch (ConfigFormatException e) {
                CoolCatCore.LOGGER.warn("Config {}: couldn't convert {} ({}); starting from defaults", this.id, old.getFileName(), e.getMessage());
                return false;
            }
            this.applyTree(root, initial, true);
            Path backup = old.resolveSibling(old.getFileName() + ".bak");
            Files.move(old, backup, StandardCopyOption.REPLACE_EXISTING);
            CoolCatCore.LOGGER.info("Config {}: converted {} to {} (the old file is kept as {})", this.id, old.getFileName(), name, backup.getFileName());
            return true;
        }
        return false;
    }

    /** Applies a file's text, as read from disk (possibly by the file watcher). */
    void readText(String text, boolean initial) {
        this.knownContentHash = text.hashCode();
        JsonObject root;
        try {
            root = this.format.read(text);
        } catch (ConfigFormatException e) {
            if (initial) {
                Path backup = this.backup();
                CoolCatCore.LOGGER.error("Config {} couldn't be parsed ({}); it was backed up to {} and replaced with defaults",
                        this.id, e.getMessage(), backup);
                this.loaded = true;
                this.save();
            } else {
                CoolCatCore.LOGGER.error("Config {} couldn't be parsed ({}); keeping the current values", this.id, e.getMessage());
            }
            return;
        }
        this.applyTree(root, initial, false);
    }

    // Reads every value from a parsed file; rewrites the file when something was missing, corrected or migrated.
    private void applyTree(JsonObject root, boolean initial, boolean forceRewrite) {
        boolean rewrite = this.migrate(root) || forceRewrite;
        Map<ConfigValueImpl<?>, Object> changes = new LinkedHashMap<>();
        for (ConfigValueImpl<?> value : this.values.values()) {
            JsonElement json = this.lookup(root, value);
            if (json == null) {
                changes.put(value, value.getDefault());
                rewrite = true;
                continue;
            }
            ValidationResult<?> result = value.decode(json);
            if (result.isError()) {
                CoolCatCore.LOGGER.warn("Config {}: {} is invalid ({}); using the default", this.id, value.path(), result.messageString());
                changes.put(value, value.getDefault());
                rewrite = true;
            } else {
                if (result.isCorrected()) {
                    CoolCatCore.LOGGER.warn("Config {}: {} was corrected ({})", this.id, value.path(), result.messageString());
                    rewrite = true;
                }
                changes.put(value, result.value());
            }
        }
        Set<ConfigValue<?>> changed = this.applyChanges(changes);
        if (this.frozen && !changed.isEmpty()) {
            CoolCatCore.LOGGER.info("Config {}: changes to {} apply after restarting the game", this.id,
                    changed.stream().map(ConfigValue::path).toList());
        }
        this.loaded = true;
        if (rewrite) this.save();
        this.fireLoaded(initial);
    }

    private void fireLoaded(boolean initial) {
        if (initial) ConfigEvents.LOADED.invoker().onLoaded(this);
        else ConfigEvents.RELOADED.invoker().onReloaded(this);
    }

    // --- Startup configs ---

    /** Fixes the live values of a startup config after its first load. */
    void freeze() {
        this.frozen = true;
    }

    public boolean isFrozen() {
        return this.frozen;
    }

    /** The value in the file: the live value, unless a change waits for the next start. */
    public Object stored(ConfigValueImpl<?> value) {
        Object next = this.nextStart.get(value);
        return next != null ? next : value.get();
    }

    private Set<ConfigValue<?>> storeForNextStart(Map<? extends ConfigValueImpl<?>, ?> changes) {
        Set<ConfigValue<?>> changed = new LinkedHashSet<>();
        synchronized (this) {
            changes.forEach((value, newValue) -> {
                if (value.sameValue(this.stored(value), newValue)) return;
                if (value.sameValue(value.get(), newValue)) this.nextStart.remove(value);
                else this.nextStart.put(value, newValue);
                changed.add(value);
            });
        }
        return Collections.unmodifiableSet(changed);
    }

    private boolean migrate(JsonObject root) {
        if (this.version <= 0) return false;
        int fileVersion = root.get(VERSION_KEY) instanceof JsonPrimitive primitive && primitive.isNumber() ? primitive.getAsInt() : 0;
        if (fileVersion > this.version) {
            CoolCatCore.LOGGER.warn("Config {} is version {}, newer than this mod's {}; reading it anyway", this.id, fileVersion, this.version);
            return false;
        }
        if (fileVersion == this.version) return false;
        for (int version = fileVersion; version < this.version; version++) {
            ConfigMigration migration = this.migrations.get(version);
            if (migration == null) continue;
            try {
                migration.migrate(root);
            } catch (RuntimeException e) {
                CoolCatCore.LOGGER.error("Migrating config {} from version {} failed", this.id, version, e);
            }
        }
        CoolCatCore.LOGGER.info("Migrated config {} from version {} to {}", this.id, fileVersion, this.version);
        return true;
    }

    // The value's current key first, then its aliases: bare keys in the same group, dotted paths from the root.
    private @Nullable JsonElement lookup(JsonObject root, ConfigValueImpl<?> value) {
        JsonElement found = find(root, value.path());
        if (found != null) return found;
        ConfigNode parent = value.parent();
        String parentPath = parent == null ? "" : parent.path();
        for (String alias : value.aliases()) {
            String path = alias.contains(".") || parentPath.isEmpty() ? alias : parentPath + "." + alias;
            found = find(root, path);
            if (found != null) return found;
        }
        return null;
    }

    private static @Nullable JsonElement find(JsonObject root, String path) {
        JsonElement current = root;
        for (String key : path.split("\\.")) {
            if (!(current instanceof JsonObject object)) return null;
            current = object.get(key);
            if (current == null) return null;
        }
        return current;
    }

    @Override
    public void save() {
        Path file = this.file;
        if (file == null || this.remote) return;
        String text = this.format.write(this.toDocument());
        synchronized (this.ioLock) {
            try {
                Files.createDirectories(file.getParent());
                Path temp = file.resolveSibling(file.getFileName() + ".tmp");
                Files.writeString(temp, text, StandardCharsets.UTF_8);
                try {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                }
                this.knownContentHash = text.hashCode();
            } catch (IOException e) {
                CoolCatCore.LOGGER.error("Couldn't save config {} to {}", this.id, file, e);
                return;
            }
        }
        ConfigEvents.SAVED.invoker().onSaved(this);
    }

    /** Whether the file's text differs from what was last read or written. */
    boolean isExternalChange(String text) {
        return text.hashCode() != this.knownContentHash;
    }

    private @Nullable Path backup() {
        Path file = this.file;
        if (file == null) return null;
        Path backup = file.resolveSibling(file.getFileName() + "." + LocalDateTime.now().format(BACKUP_TIME) + ".bak");
        try {
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            return backup;
        } catch (IOException e) {
            CoolCatCore.LOGGER.error("Couldn't back up config {}", this.id, e);
            return null;
        }
    }

    // A world config missing from the world starts from the modpack's copy in defaultconfigs, if there is one.
    private boolean copyDefaultConfig(Path file) throws IOException {
        if (this.scope != ConfigScope.WORLD) return false;
        Path defaults = ConfigManager.defaultConfigFolder().resolve(this.fileName());
        if (Files.notExists(defaults)) return false;
        Files.createDirectories(file.getParent());
        Files.copy(defaults, file);
        CoolCatCore.LOGGER.info("Copied config {} from {}", this.id, defaults);
        return true;
    }

    /** Sets defaults without a file, as a world config does between worlds. */
    void unload() {
        ConfigEvents.UNLOADING.invoker().onUnloading(this);
        this.bindFile(null);
        this.resetAll();
    }

    // --- Documents ---

    public ConfigDocument.Section toDocument() {
        ConfigDocument.Section document = this.section(this.root);
        if (this.version > 0) {
            Map<String, ConfigDocument.Node> children = new LinkedHashMap<>();
            children.put(VERSION_KEY, new ConfigDocument.Entry(List.of("The file's version; don't change it."), new JsonPrimitive(this.version)));
            children.putAll(document.children());
            document = new ConfigDocument.Section(document.comment(), children);
        }
        List<String> header = new ArrayList<>(this.comment);
        if (header.isEmpty()) header.add(this.title().getString());
        return new ConfigDocument.Section(header, document.children());
    }

    private ConfigDocument.Section section(ConfigGroupImpl group) {
        Map<String, ConfigDocument.Node> children = new LinkedHashMap<>();
        for (ConfigNode child : group.children()) {
            if (child instanceof ConfigGroupImpl subgroup) {
                children.put(child.key(), this.section(subgroup));
            } else if (child instanceof ConfigValueImpl<?> value) {
                children.put(child.key(), new ConfigDocument.Entry(this.valueComment(value), value.encodeUnchecked(this.stored(value))));
            }
        }
        return new ConfigDocument.Section(group.comment(), children);
    }

    private <T> List<String> valueComment(ConfigValueImpl<T> value) {
        List<String> lines = new ArrayList<>(value.comment());
        value.type().describe().forEach(line -> lines.add(line.getString()));
        String defaultValue = value.type().format(value.getDefault());
        if (defaultValue.length() > MAX_DEFAULT_COMMENT_LENGTH) defaultValue = defaultValue.substring(0, MAX_DEFAULT_COMMENT_LENGTH) + "…";
        lines.add(Component.translatableWithFallback("config.coolcatcore.info.default", "Default: %s", defaultValue).getString());
        switch (value.restartRequirement()) {
            case GAME -> lines.add(Component.translatableWithFallback("config.coolcatcore.info.restart_game", "Requires restarting the game").getString());
            case WORLD -> lines.add(Component.translatableWithFallback("config.coolcatcore.info.restart_world", "Requires rejoining the world").getString());
            default -> {
            }
        }
        Component sync = syncNote(value);
        if (sync != null) lines.add(sync.getString());
        return lines;
    }

    /** How the value relates to the server's, when that isn't simply "synced". */
    public static @Nullable Component syncNote(ConfigValueImpl<?> value) {
        ConfigScope scope = value.config().scope();
        if (scope == ConfigScope.STARTUP) {
            if (!value.isSynced()) return Component.translatableWithFallback("config.coolcatcore.info.local", "Each side keeps its own value");
            return value.startupSync() == StartupSync.USE_SERVER
                    ? Component.translatableWithFallback("config.coolcatcore.info.use_server", "Players use the server's value while connected")
                    : Component.translatableWithFallback("config.coolcatcore.info.require_match", "Players must have the same value as the server");
        }
        if (scope.isSynced() && !value.isSynced()) {
            return Component.translatableWithFallback("config.coolcatcore.info.server_only", "Kept on the server; not sent to players");
        }
        return null;
    }

    // --- Flat snapshots, as the network carries them: dotted path -> encoded value ---

    /**
     * What the server sends in play: its synced values, or for a startup config only those clients take over
     * ({@link StartupSync#USE_SERVER}); the rest were checked before joining.
     */
    public JsonObject syncSnapshot() {
        JsonObject flat = new JsonObject();
        for (ConfigValueImpl<?> value : this.values.values()) {
            if (!value.isSynced()) continue;
            if (this.scope == ConfigScope.STARTUP && value.startupSync() != StartupSync.USE_SERVER) continue;
            flat.add(value.path(), value.encodeCurrent());
        }
        return flat;
    }

    public JsonObject snapshot(boolean syncedOnly) {
        JsonObject flat = new JsonObject();
        for (ConfigValueImpl<?> value : this.values.values()) {
            if (!syncedOnly || value.isSynced()) flat.add(value.path(), value.encodeCurrent());
        }
        return flat;
    }

    /**
     * Applies a flat snapshot. Strict application (a player's edit) needs every value valid as is; otherwise
     * corrections are accepted. Unknown paths and invalid values are skipped and reported.
     */
    public SnapshotResult applySnapshot(JsonObject flat, boolean strict) {
        return this.applySnapshot(flat, strict, false);
    }

    /** As {@link #applySnapshot(JsonObject, boolean)}; {@code live} changes live values even of a frozen config. */
    public SnapshotResult applySnapshot(JsonObject flat, boolean strict, boolean live) {
        List<Component> errors = new ArrayList<>();
        Map<ConfigValueImpl<?>, Object> changes = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : flat.entrySet()) {
            ConfigValueImpl<?> value = this.values.get(entry.getKey());
            if (value == null) {
                errors.add(Component.translatableWithFallback("config.coolcatcore.error.unknown", "Unknown setting %s", entry.getKey()));
                continue;
            }
            ValidationResult<?> result = value.decode(entry.getValue());
            if (result.isError() || (strict && result.isCorrected())) {
                errors.add(Component.literal(value.path() + ": ").append(result.message().orElse(Component.empty())));
            } else {
                changes.put(value, result.value());
            }
        }
        return new SnapshotResult(live ? this.applyLive(changes) : this.applyChanges(changes), errors);
    }

    public record SnapshotResult(Set<ConfigValue<?>> changed, List<Component> errors) {}

    @Override
    public String toString() {
        return "Config[" + this.id + ", " + this.scope + "]";
    }
}
