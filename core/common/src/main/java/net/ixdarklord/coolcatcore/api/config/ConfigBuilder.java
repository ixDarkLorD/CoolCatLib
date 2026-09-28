package net.ixdarklord.coolcatcore.api.config;

import com.mojang.serialization.Codec;
import net.ixdarklord.coolcatcore.api.config.format.ConfigFormat;
import net.ixdarklord.coolcatcore.api.config.format.ConfigFormats;
import net.ixdarklord.coolcatcore.api.config.type.ConfigType;
import net.ixdarklord.coolcatcore.api.config.type.ConfigTypes;
import net.ixdarklord.coolcatcore.internal.config.ConfigGroupImpl;
import net.ixdarklord.coolcatcore.internal.config.ConfigImpl;
import net.ixdarklord.coolcatcore.internal.config.ConfigManager;
import net.ixdarklord.coolcatcore.internal.config.ConfigNodeImpl;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.Permissions;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Declares a config: its settings, then its groups and values, then {@link #build()} registers and loads it.
 * <p>
 * Values are added to the current group; {@link #push} opens a nested group and {@link #pop} closes it, or
 * {@link #group(String, Consumer)} does both around a block. Each value starts with a typed method
 * ({@link #bool}, {@link #intValue}, {@link #list}...), takes options, and ends with {@code build()}, which returns its
 * handle:
 * <pre>{@code
 * ConfigBuilder builder = Config.builder("mymod", ConfigScope.SERVER);
 * ConfigValue<Integer> maxHomes = builder.intValue("maxHomes", 3).range(0, 64).comment("Homes per player.").build();
 * builder.push("webhook");
 * ConfigValue<String> url = builder.string("url", "").serverOnly().build();
 * builder.pop();
 * Config config = builder.build();
 * }</pre>
 */
public final class ConfigBuilder {
    private final String modId;
    private final ConfigScope scope;
    private final ConfigGroupImpl root = new ConfigGroupImpl("root", null, List.of(), null, false);
    private final Deque<ConfigGroupImpl> groups = new ArrayDeque<>();
    private final List<String> comment = new ArrayList<>();
    private final Map<Integer, ConfigMigration> migrations = new HashMap<>();
    private final List<PresetDeclaration> presets = new ArrayList<>();
    private String name;
    private @Nullable String fileName;
    private ConfigFormat format = ConfigFormats.TOML;
    private int version;
    private Permission editPermission = Permissions.COMMANDS_GAMEMASTER;
    private boolean hotReload = true;
    private @Nullable ConfigTheme theme;
    private boolean built;

    ConfigBuilder(String modId, ConfigScope scope) {
        this.modId = modId;
        this.scope = scope;
        this.name = scope.defaultName();
        this.groups.push(this.root);
    }

    // --- Config settings ---

    /** The config's name, the path of its id ({@code mymod:<name>}); the scope's name by default. */
    public ConfigBuilder name(String name) {
        if (!Identifier.isValidPath(name)) throw new IllegalArgumentException("Invalid config name " + name);
        this.name = name;
        return this;
    }

    /**
     * The file's path without extension, relative to the config folder (or the world's {@code serverconfig}):
     * {@code <modid>-<name>} by default. May contain folders: {@code "mymod/client"}.
     */
    public ConfigBuilder fileName(String fileName) {
        this.fileName = fileName;
        return this;
    }

    /**
     * The file's format: {@link ConfigFormats#TOML} by default. Changing it later keeps players' values: a file in the
     * other built-in format is converted once (and kept as a {@code .bak}).
     */
    public ConfigBuilder format(ConfigFormat format) {
        this.format = format;
        return this;
    }

    /** Lines at the top of the file. */
    public ConfigBuilder comment(String... lines) {
        Collections.addAll(this.comment, lines);
        return this;
    }

    /** The file version; files of older versions go through the {@link #migration}s. */
    public ConfigBuilder version(int version) {
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
        this.version = version;
        return this;
    }

    /** Upgrades files of version {@code fromVersion} to {@code fromVersion + 1}. */
    public ConfigBuilder migration(int fromVersion, ConfigMigration migration) {
        this.migrations.put(fromVersion, migration);
        return this;
    }

    /**
     * Who may change a {@linkplain ConfigScope#isSynced() synced} config from a client (the in-game screen): game
     * masters ({@link Permissions#COMMANDS_GAMEMASTER}) by default. The host of a singleplayer world always may.
     */
    public ConfigBuilder editPermission(Permission permission) {
        this.editPermission = permission;
        return this;
    }

    /** Whether edits to the file while the game runs are loaded automatically; on by default. */
    public ConfigBuilder hotReload(boolean hotReload) {
        this.hotReload = hotReload;
        return this;
    }

    /** How this config's screen looks; the mod's theme ({@link ConfigTheme#setForMod}) by default. */
    public ConfigBuilder theme(ConfigTheme theme) {
        this.theme = theme;
        return this;
    }

    /**
     * A background texture for this config's screen, by its full path ({@code mymod:textures/gui/background.png}),
     * keeping the rest of the theme.
     */
    public ConfigBuilder background(Identifier texture) {
        this.theme = (this.theme != null ? this.theme : ConfigTheme.forMod(this.modId)).toBuilder().background(texture).build();
        return this;
    }

    // --- Groups ---

    /** Opens a nested group; values and groups go into it until {@link #pop()}. */
    public ConfigBuilder push(String key, String... comment) {
        this.checkOpen();
        ConfigGroupImpl group = new ConfigGroupImpl(key, this.current(), List.of(comment), null, false);
        this.current().add(group);
        this.groups.push(group);
        return this;
    }

    /** Closes the group opened last. */
    public ConfigBuilder pop() {
        if (this.groups.size() <= 1) throw new IllegalStateException("No group to pop");
        this.groups.pop();
        return this;
    }

    /** Declares a nested group's contents in a block. */
    public ConfigBuilder group(String key, Consumer<ConfigBuilder> contents) {
        return this.group(key, List.of(), contents);
    }

    public ConfigBuilder group(String key, List<String> comment, Consumer<ConfigBuilder> contents) {
        this.push(key, comment.toArray(String[]::new));
        contents.accept(this);
        return this.pop();
    }

    // --- Values ---

    public EntryBuilder<Boolean> bool(String key, boolean defaultValue) {
        return this.value(key, ConfigTypes.BOOLEAN, defaultValue);
    }

    public NumberEntryBuilder<Integer> intValue(String key, int defaultValue) {
        return new NumberEntryBuilder<>(this, this.current(), key, ConfigTypes.INT, defaultValue);
    }

    public NumberEntryBuilder<Long> longValue(String key, long defaultValue) {
        return new NumberEntryBuilder<>(this, this.current(), key, ConfigTypes.LONG, defaultValue);
    }

    public NumberEntryBuilder<Float> floatValue(String key, float defaultValue) {
        return new NumberEntryBuilder<>(this, this.current(), key, ConfigTypes.FLOAT, defaultValue);
    }

    public NumberEntryBuilder<Double> doubleValue(String key, double defaultValue) {
        return new NumberEntryBuilder<>(this, this.current(), key, ConfigTypes.DOUBLE, defaultValue);
    }

    public StringEntryBuilder string(String key, String defaultValue) {
        return new StringEntryBuilder(this, this.current(), key, ConfigTypes.STRING, defaultValue);
    }

    public <E extends Enum<E>> EntryBuilder<E> enumValue(String key, E defaultValue) {
        return this.value(key, ConfigTypes.enumOf(defaultValue.getDeclaringClass()), defaultValue);
    }

    /** An opaque color, packed as {@code 0xRRGGBB}. */
    public EntryBuilder<Integer> color(String key, int defaultValue) {
        return this.value(key, ConfigTypes.COLOR, defaultValue | 0xFF000000);
    }

    /** A color with alpha, packed as {@code 0xAARRGGBB}. */
    public EntryBuilder<Integer> colorWithAlpha(String key, int defaultValue) {
        return this.value(key, ConfigTypes.COLOR_ALPHA, defaultValue);
    }

    public EntryBuilder<Identifier> identifier(String key, Identifier defaultValue) {
        return this.value(key, ConfigTypes.IDENTIFIER, defaultValue);
    }

    /** An id suggested from a built-in registry, like {@code Registries.BLOCK}. */
    public EntryBuilder<Identifier> identifier(String key, Identifier defaultValue, ResourceKey<? extends Registry<?>> registry) {
        return this.value(key, ConfigTypes.identifier(registry), defaultValue);
    }

    public <E> ListEntryBuilder<E> list(String key, ConfigType<E> elementType, List<E> defaultValue) {
        return new ListEntryBuilder<>(this, this.current(), key, ConfigTypes.listOf(elementType), defaultValue);
    }

    public ListEntryBuilder<String> stringList(String key, List<String> defaultValue) {
        return this.list(key, ConfigTypes.STRING, defaultValue);
    }

    /** A value of any type, including a mod's own {@link ConfigType}. */
    public <T> EntryBuilder<T> value(String key, ConfigType<T> type, T defaultValue) {
        return new EntryBuilder<>(this, this.current(), key, type, defaultValue);
    }

    /** A value of any codec, edited as JSON. */
    public <T> EntryBuilder<T> codec(String key, Codec<T> codec, T defaultValue) {
        return this.value(key, ConfigTypes.codec(codec), defaultValue);
    }

    // --- Presets ---

    /**
     * A named set of values, translated as {@code config.<modid>.<config>.preset.<name>}. The block runs at
     * {@link #build()}, so it may refer to values declared after it.
     */
    public ConfigBuilder preset(String name, Consumer<ConfigPreset.Builder> values) {
        return this.preset(name, null, values);
    }

    public ConfigBuilder preset(String name, @Nullable Component displayName, Consumer<ConfigPreset.Builder> values) {
        this.presets.add(new PresetDeclaration(name, displayName, values));
        return this;
    }

    // --- Building ---

    /**
     * Registers the config and loads its file (a world config loads when a server starts).
     *
     * @throws IllegalStateException    when a group is still open, or the config was already built
     * @throws IllegalArgumentException when a config with the same id exists
     */
    public Config build() {
        this.checkOpen();
        if (this.groups.size() > 1) throw new IllegalStateException("Group " + this.current().path() + " was never popped");
        this.built = true;

        Map<String, ConfigPreset> presets = new LinkedHashMap<>();
        for (PresetDeclaration declaration : this.presets) {
            ConfigPreset.Builder builder = new ConfigPreset.Builder();
            declaration.values.accept(builder);
            Component displayName = declaration.displayName != null ? declaration.displayName
                    : Component.translatableWithFallback("config." + this.modId + "." + this.name + ".preset." + declaration.name, ConfigNodeImpl.prettify(declaration.name));
            presets.put(declaration.name, new ConfigPreset(declaration.name, displayName, builder.values()));
        }

        Identifier id = Identifier.fromNamespaceAndPath(this.modId, this.name);
        String fileName = this.fileName != null ? this.fileName : this.modId + "-" + this.name;
        ConfigImpl config = new ConfigImpl(id, this.scope, this.format, this.root, this.comment, this.version, this.migrations,
                this.editPermission, this.hotReload, fileName, presets, this.theme);
        ConfigManager.register(config);
        return config;
    }

    void checkOpen() {
        if (this.built) throw new IllegalStateException("Config " + this.modId + ":" + this.name + " was already built");
    }

    private ConfigGroupImpl current() {
        return this.groups.peek();
    }

    private record PresetDeclaration(String name, @Nullable Component displayName, Consumer<ConfigPreset.Builder> values) {}
}
