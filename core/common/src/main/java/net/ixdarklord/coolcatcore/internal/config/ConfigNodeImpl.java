package net.ixdarklord.coolcatcore.internal.config;

import net.ixdarklord.coolcatcore.api.config.Config;
import net.ixdarklord.coolcatcore.api.config.ConfigNode;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public abstract class ConfigNodeImpl implements ConfigNode {
    private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9_-]+");
    // Word boundaries of camelCase, snake_case and kebab-case keys.
    private static final Pattern WORD_BOUNDARY = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])|[_-]+");

    private final String key;
    private final @Nullable ConfigGroupImpl parent;
    private final List<String> comment;
    private final @Nullable String translationKey;
    private final boolean hidden;
    private @Nullable ConfigImpl config;

    protected ConfigNodeImpl(String key, @Nullable ConfigGroupImpl parent, List<String> comment, @Nullable String translationKey, boolean hidden) {
        if (parent != null && !VALID_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid config key '" + key + "': use letters, digits, _ and -");
        }
        this.key = key;
        this.parent = parent;
        this.comment = List.copyOf(comment);
        this.translationKey = translationKey;
        this.hidden = hidden;
    }

    void attach(ConfigImpl config) {
        this.config = config;
    }

    @Override
    public String key() {
        return this.key;
    }

    @Override
    public String path() {
        if (this.parent == null) return "";
        String parentPath = this.parent.path();
        return parentPath.isEmpty() ? this.key : parentPath + "." + this.key;
    }

    @Override
    public ConfigImpl config() {
        if (this.config == null) throw new IllegalStateException("The config holding " + this.key + " hasn't been built yet");
        return this.config;
    }

    /** The config, or null before the builder finished. */
    public @Nullable ConfigImpl configOrNull() {
        return this.config;
    }

    @Override
    public @Nullable ConfigGroupImpl parent() {
        return this.parent;
    }

    @Override
    public List<String> comment() {
        return this.comment;
    }

    @Override
    public String translationKey() {
        if (this.translationKey != null) return this.translationKey;
        Config config = this.config();
        String base = "config." + config.modId() + "." + config.name();
        return this.parent == null ? base : base + "." + this.path();
    }

    @Override
    public Component displayName() {
        return Component.translatableWithFallback(this.translationKey(), prettify(this.key));
    }

    @Override
    public boolean isHidden() {
        return this.hidden;
    }

    /** {@code maxCount}, {@code max_count} and {@code max-count} all read "Max Count". */
    public static String prettify(String key) {
        StringBuilder out = new StringBuilder();
        for (String word : WORD_BOUNDARY.split(key)) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }
}
