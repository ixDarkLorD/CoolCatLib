package net.ixdarklord.coolcatcore.api.config;

import net.ixdarklord.coolcatcore.api.config.type.ConfigType;
import net.ixdarklord.coolcatcore.internal.config.ConfigGroupImpl;

/**
 * An entry whose type has no builder options of its own (booleans, enums, colors, ids, custom types).
 */
public final class EntryBuilder<T> extends AbstractEntryBuilder<T, EntryBuilder<T>> {
    private final ConfigType<T> type;

    EntryBuilder(ConfigBuilder owner, ConfigGroupImpl group, String key, ConfigType<T> type, T defaultValue) {
        super(owner, group, key, defaultValue);
        this.type = type;
    }

    @Override
    protected ConfigType<T> type() {
        return this.type;
    }
}
