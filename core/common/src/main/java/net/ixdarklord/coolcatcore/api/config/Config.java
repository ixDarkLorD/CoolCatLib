package net.ixdarklord.coolcatcore.api.config;

import net.ixdarklord.coolcatcore.api.config.format.ConfigFormat;
import net.ixdarklord.coolcatcore.internal.config.ConfigManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permission;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A registered config: a tree of {@link ConfigGroup groups} and typed {@link ConfigValue values}, stored in one file.
 * <p>
 * Declare one with a {@link ConfigBuilder}, keeping the value handles in static fields:
 * <pre>{@code
 * public final class MyModConfig {
 *     private static final ConfigBuilder BUILDER = Config.builder("mymod", ConfigScope.CLIENT);
 *
 *     public static final ConfigValue<Boolean> SHOW_HUD = BUILDER.bool("showHud", true)
 *             .comment("Draws the overlay.").build();
 *
 *     static { BUILDER.push("rendering"); }
 *     public static final ConfigValue<Integer> PARTICLES = BUILDER.intValue("particles", 32)
 *             .range(0, 256).slider().enabledWhen(SHOW_HUD).build();
 *     static { BUILDER.pop(); }
 *
 *     public static final Config CONFIG = BUILDER.build();
 * }
 * }</pre>
 * or from an annotated class with {@link net.ixdarklord.coolcatcore.api.config.annotation.ConfigObject}. Building
 * registers the config and loads its file; commands, hot reloading and (for synced scopes) server sync then work
 * without more code, and so do the config screens of Glazed Menu (or Configured), when a player has it.
 */
@ApiStatus.NonExtendable
public interface Config {
    /** Starts declaring a config named after its scope ({@code mymod:client}). */
    static ConfigBuilder builder(String modId, ConfigScope scope) {
        return new ConfigBuilder(modId, scope);
    }

    /** A registered config by its id ({@code mymod:client}). */
    static Optional<Config> get(Identifier id) {
        return Optional.ofNullable(ConfigManager.get(id));
    }

    /** Every registered config, in registration order. */
    static List<Config> all() {
        return List.copyOf(ConfigManager.all());
    }

    /** A mod's registered configs, in registration order. */
    static List<Config> forMod(String modId) {
        return List.copyOf(ConfigManager.forMod(modId));
    }

    /** {@code <modid>:<name>}. */
    Identifier id();

    default String modId() {
        return this.id().getNamespace();
    }

    default String name() {
        return this.id().getPath();
    }

    ConfigScope scope();

    ConfigFormat format();

    ConfigGroup root();

    /** The config's title: {@code config.<modid>.<name>.title}, falling back to the mod id and name. */
    Component title();

    /** How the config's screen looks: its own theme, or the mod's. */
    ConfigTheme theme();

    /** The file's comment lines. */
    List<String> comment();

    /** The file version values are migrated to; 0 when the config isn't versioned. */
    int version();

    /** Who may change a synced config from a client. */
    Permission editPermission();

    /** The config file, or null while it has none (a client config on a dedicated server). */
    @Nullable Path filePath();

    /** Whether the values come from the file (or a server) rather than being defaults with no file. */
    boolean isLoaded();

    /**
     * Whether this client currently shows a server's values: the config is synced and the client is connected to a
     * remote server. Local changes aren't saved meanwhile, and the client's own values come back on disconnecting.
     */
    boolean isRemote();

    /** Whether a {@link ConfigScope#STARTUP} config has saved changes that apply after a restart. */
    default boolean isRestartPending() {
        return this.values().anyMatch(ConfigValue::isRestartPending);
    }

    /** Writes the current values to the file. Does nothing while the config has no file or is {@linkplain #isRemote() remote}. */
    void save();

    /** Reads the file again, notifying listeners of whatever changed. */
    void reload();

    /** Sets every value back to its default, in memory; {@link #save()} writes it. */
    void resetAll();

    /** The value at a dotted path, like {@code "rendering.particles"}. */
    Optional<ConfigValue<?>> find(String path);

    /** Every value, depth first. */
    default Stream<ConfigValue<?>> values() {
        return this.root().values();
    }

    /** The presets declared on the builder, by name. */
    Map<String, ConfigPreset> presets();

    /** Sets a preset's values, in memory; {@link #save()} writes them. */
    void applyPreset(ConfigPreset preset);

    /** Called with the values that changed, whenever any do, on the thread that changed them. */
    void addListener(ChangeListener listener);

    boolean removeListener(ChangeListener listener);

    @FunctionalInterface
    interface ChangeListener {
        void onChanged(Config config, Set<ConfigValue<?>> changed);
    }
}
