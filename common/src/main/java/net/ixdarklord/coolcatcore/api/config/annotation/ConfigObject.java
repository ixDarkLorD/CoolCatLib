package net.ixdarklord.coolcatcore.api.config.annotation;

import net.ixdarklord.coolcatcore.api.config.Config;
import net.ixdarklord.coolcatcore.api.config.ConfigBuilder;
import net.ixdarklord.coolcatcore.api.config.ConfigScope;
import net.ixdarklord.coolcatcore.internal.config.ConfigObjectBinder;

/**
 * A config declared as a plain class: every non-static, non-transient field becomes an entry, with the field's
 * initial value as its default, and nested objects become groups. The fields always hold the current values, so the
 * mod reads them directly:
 * <pre>{@code
 * @ConfigEntry.Comment("MyMod's client settings")
 * public class MyClientConfig {
 *     @ConfigEntry.Comment("Draws the overlay.")
 *     public boolean showHud = true;
 *
 *     @ConfigEntry.Range(min = 0, max = 256) @ConfigEntry.Slider
 *     public int particles = 32;
 *
 *     public Rendering rendering = new Rendering();
 *
 *     public static class Rendering {
 *         @ConfigEntry.Color public int tint = 0xFF8800;
 *         public List<String> hiddenItems = List.of("minecraft:barrier");
 *     }
 * }
 *
 * public static final ConfigObject<MyClientConfig> CONFIG = ConfigObject.register("mymod", ConfigScope.CLIENT, new MyClientConfig());
 * if (CONFIG.get().showHud) ...
 * }</pre>
 * Supported field types: {@code boolean}, {@code int}, {@code long}, {@code float}, {@code double} (and their boxes),
 * {@link String}, enums, {@link net.minecraft.resources.ResourceLocation}, and {@link java.util.List}s of those. Assign new
 * lists rather than changing them in place: the lists held are immutable.
 *
 * @param <T> the class declaring the entries
 */
public final class ConfigObject<T> {
    private final T instance;
    private final Config config;
    private final ConfigObjectBinder binder;

    private ConfigObject(T instance, Config config, ConfigObjectBinder binder) {
        this.instance = instance;
        this.config = config;
        this.binder = binder;
    }

    /** Registers the instance's fields as a config named after the scope. */
    public static <T> ConfigObject<T> register(String modId, ConfigScope scope, T instance) {
        return register(Config.builder(modId, scope), instance);
    }

    /**
     * Registers the instance's fields with a builder whose settings (name, format, version...) are already set.
     *
     * @throws IllegalArgumentException when a field's type isn't supported
     */
    public static <T> ConfigObject<T> register(ConfigBuilder builder, T instance) {
        ConfigObjectBinder binder = new ConfigObjectBinder(instance);
        Config config = binder.bind(builder);
        return new ConfigObject<>(instance, config, binder);
    }

    /** The instance whose fields hold the current values. */
    public T get() {
        return this.instance;
    }

    public Config config() {
        return this.config;
    }

    /**
     * Takes values assigned to the fields by code, then writes the file. Invalid field values are put back to the
     * config's values.
     */
    public void save() {
        this.binder.pushFields();
        this.config.save();
    }
}
