package net.ixdarklord.coolcatcore.api.config;

import net.ixdarklord.coolcatcore.api.config.type.ConfigType;
import net.ixdarklord.coolcatcore.internal.config.ConfigGroupImpl;
import net.ixdarklord.coolcatcore.internal.config.ConfigValueImpl;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The options every entry has. Finish with {@link #build()}, which adds the value to the group that was current
 * when the entry was started.
 *
 * @param <T> the value's type
 * @param <B> the builder's own type, for chaining
 */
public abstract class AbstractEntryBuilder<T, B extends AbstractEntryBuilder<T, B>> {
    private final ConfigBuilder owner;
    private final ConfigGroupImpl group;
    private final String key;
    private final T defaultValue;
    private final List<String> comment = new ArrayList<>();
    private final List<String> aliases = new ArrayList<>();
    private final List<Function<T, Component>> validators = new ArrayList<>();
    private final List<ConfigValue.ChangeListener<T>> listeners = new ArrayList<>();
    private @Nullable String translationKey;
    private boolean hidden;
    private boolean synced = true;
    private StartupSync startupSync = StartupSync.REQUIRE_MATCH;
    private RestartRequirement restart = RestartRequirement.NONE;
    private @Nullable ConfigDependency<?> dependency;
    private boolean built;

    AbstractEntryBuilder(ConfigBuilder owner, ConfigGroupImpl group, String key, T defaultValue) {
        this.owner = owner;
        this.group = group;
        this.key = key;
        this.defaultValue = defaultValue;
    }

    /** The type the entry is built with, once every option is set. */
    protected abstract ConfigType<T> type();

    /** Lines written above the value in the file; also its tooltip when there's no {@code .tooltip} translation. */
    public B comment(String... lines) {
        Collections.addAll(this.comment, lines);
        return this.self();
    }

    /** A translation key for the name, instead of {@code config.<modid>.<config>.<path>}. */
    public B translation(String translationKey) {
        this.translationKey = translationKey;
        return this.self();
    }

    /** Keeps the value out of the config screen; it's still in the file and commands. */
    public B hidden() {
        this.hidden = true;
        return this.self();
    }

    public B requiresRestart(RestartRequirement restart) {
        this.restart = restart;
        return this.self();
    }

    /** Changes take effect after restarting the game. */
    public B requiresGameRestart() {
        return this.requiresRestart(RestartRequirement.GAME);
    }

    /** Changes take effect after rejoining the world. */
    public B requiresWorldRestart() {
        return this.requiresRestart(RestartRequirement.WORLD);
    }

    /**
     * In a synced config, keeps the value on the server: it isn't sent to players, who can't see or change it (only
     * the host of a singleplayer world can). For secrets like tokens and passwords.
     */
    public B serverOnly() {
        this.synced = false;
        return this.self();
    }

    /**
     * In a {@link ConfigScope#STARTUP} config, lets each side keep its own value: it isn't compared with the
     * server's. For client-side details, like a texture variant. (The same as {@link #serverOnly()}.)
     */
    public B localOnly() {
        return this.serverOnly();
    }

    /**
     * In a {@link ConfigScope#STARTUP} config, lets a client whose value differs join, and uses the server's value
     * while connected; see {@link StartupSync#USE_SERVER}. By default the values must match.
     */
    public B useServerValue() {
        this.startupSync = StartupSync.USE_SERVER;
        return this.self();
    }

    /**
     * Keys this value had before: a bare key is looked up in the same group, a dotted path from the root. The file
     * is rewritten with the current key.
     */
    public B aliases(String... oldKeys) {
        Collections.addAll(this.aliases, oldKeys);
        return this.self();
    }

    /** An extra check beyond the type's constraints. */
    public B validator(Predicate<? super T> check, Component errorMessage) {
        this.validators.add(value -> check.test(value) ? null : errorMessage);
        return this.self();
    }

    /** An extra check returning an error message, or null for a valid value. */
    public B validator(Function<? super T, Component> check) {
        this.validators.add(check::apply);
        return this.self();
    }

    /** Greys the value out in the config screen while {@code other} is off. */
    public B enabledWhen(ConfigValue<Boolean> other) {
        return this.enabledWhen(other, Boolean::booleanValue);
    }

    /** Greys the value out in the config screen while {@code other}'s value doesn't meet the condition. */
    public <V> B enabledWhen(ConfigValue<V> other, Predicate<? super V> condition) {
        this.dependency = new ConfigDependency<>(other, condition);
        return this.self();
    }

    /** Called whenever the value changes; see {@link ConfigValue#addListener}. */
    public B listener(ConfigValue.ChangeListener<T> listener) {
        this.listeners.add(listener);
        return this.self();
    }

    /**
     * Adds the value to the config.
     *
     * @throws IllegalArgumentException when the default value isn't valid, or the key is taken
     * @throws IllegalStateException    when the entry or the config was already built
     */
    public ConfigValue<T> build() {
        if (this.built) throw new IllegalStateException("Entry " + this.key + " was already built");
        this.owner.checkOpen();
        this.built = true;
        ConfigValueImpl<T> value = new ConfigValueImpl<>(this.key, this.group, this.comment, this.translationKey, this.hidden, this.type(),
                this.defaultValue, this.restart, this.synced, this.startupSync, this.aliases, this.validators, this.dependency);
        this.listeners.forEach(value::addListener);
        this.group.add(value);
        return value;
    }

    @SuppressWarnings("unchecked")
    protected final B self() {
        return (B) this;
    }
}
