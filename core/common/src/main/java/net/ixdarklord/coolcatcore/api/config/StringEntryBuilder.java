package net.ixdarklord.coolcatcore.api.config;

import net.ixdarklord.coolcatcore.api.config.type.ConfigType;
import net.ixdarklord.coolcatcore.api.config.type.StringType;
import net.ixdarklord.coolcatcore.internal.config.ConfigGroupImpl;

/**
 * A text entry: optionally limited in length, required, or matched against a pattern.
 */
public final class StringEntryBuilder extends AbstractEntryBuilder<String, StringEntryBuilder> {
    private StringType type;

    StringEntryBuilder(ConfigBuilder owner, ConfigGroupImpl group, String key, StringType type, String defaultValue) {
        super(owner, group, key, defaultValue);
        this.type = type;
    }

    public StringEntryBuilder maxLength(int maxLength) {
        this.type = this.type.withMaxLength(maxLength);
        return this;
    }

    /** Rejects blank text. */
    public StringEntryBuilder notEmpty() {
        this.type = this.type.withNotEmpty(true);
        return this;
    }

    /** The whole text must match this regular expression. */
    public StringEntryBuilder pattern(String regex) {
        this.type = this.type.withPattern(regex);
        return this;
    }

    @Override
    protected ConfigType<String> type() {
        return this.type;
    }
}
