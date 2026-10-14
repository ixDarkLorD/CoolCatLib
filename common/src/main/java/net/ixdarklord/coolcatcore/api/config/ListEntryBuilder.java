package net.ixdarklord.coolcatcore.api.config;

import net.ixdarklord.coolcatcore.api.config.type.ConfigType;
import net.ixdarklord.coolcatcore.api.config.type.ListType;
import net.ixdarklord.coolcatcore.internal.config.ConfigGroupImpl;

import java.util.List;

/**
 * A list entry; its elements are checked by the element type given to the {@link ConfigBuilder}.
 */
public final class ListEntryBuilder<E> extends AbstractEntryBuilder<List<E>, ListEntryBuilder<E>> {
    private ListType<E> type;

    ListEntryBuilder(ConfigBuilder owner, ConfigGroupImpl group, String key, ListType<E> type, List<E> defaultValue) {
        super(owner, group, key, List.copyOf(defaultValue));
        this.type = type;
    }

    public ListEntryBuilder<E> size(int minSize, int maxSize) {
        this.type = this.type.withSize(minSize, maxSize);
        return this;
    }

    public ListEntryBuilder<E> maxSize(int maxSize) {
        return this.size(this.type.minSize(), maxSize);
    }

    public ListEntryBuilder<E> minSize(int minSize) {
        return this.size(minSize, this.type.maxSize());
    }

    @Override
    protected ConfigType<List<E>> type() {
        return this.type;
    }
}
