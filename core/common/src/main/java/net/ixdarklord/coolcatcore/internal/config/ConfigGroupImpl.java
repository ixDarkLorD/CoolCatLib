package net.ixdarklord.coolcatcore.internal.config;

import net.ixdarklord.coolcatcore.api.config.ConfigGroup;
import net.ixdarklord.coolcatcore.api.config.ConfigNode;
import net.ixdarklord.coolcatcore.api.config.ConfigValue;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class ConfigGroupImpl extends ConfigNodeImpl implements ConfigGroup {
    private final Map<String, ConfigNodeImpl> children = new LinkedHashMap<>();

    public ConfigGroupImpl(String key, @Nullable ConfigGroupImpl parent, List<String> comment, @Nullable String translationKey, boolean hidden) {
        super(key, parent, comment, translationKey, hidden);
    }

    public void add(ConfigNodeImpl node) {
        if (this.children.putIfAbsent(node.key(), node) != null) {
            throw new IllegalArgumentException("Duplicate config key '" + node.key() + "' in " + (this.path().isEmpty() ? "the root" : this.path()));
        }
    }

    @Override
    void attach(ConfigImpl config) {
        super.attach(config);
        this.children.values().forEach(child -> child.attach(config));
    }

    @Override
    public List<ConfigNode> children() {
        return List.copyOf(this.children.values());
    }

    @Override
    public @Nullable ConfigNodeImpl child(String key) {
        return this.children.get(key);
    }

    @Override
    public Stream<ConfigValue<?>> values() {
        return this.children.values().stream().flatMap(child -> child instanceof ConfigGroupImpl group ? group.values() : Stream.of((ConfigValue<?>) child));
    }
}
