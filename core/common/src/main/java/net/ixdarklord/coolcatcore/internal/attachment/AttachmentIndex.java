package net.ixdarklord.coolcatcore.internal.attachment;

import net.ixdarklord.coolcatcore.api.attachment.Attachment;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Every built Attachment by id, for reading saved and synced values back.
public final class AttachmentIndex {
    private static final Map<Identifier, Attachment<?>> KEYS = new ConcurrentHashMap<>();

    private AttachmentIndex() {}

    public static void register(Attachment<?> key) {
        if (KEYS.putIfAbsent(key.id(), key) != null) {
            throw new IllegalArgumentException("A data key with the id " + key.id() + " is already registered");
        }
    }

    public static @Nullable Attachment<?> get(Identifier id) {
        return KEYS.get(id);
    }

    public static Collection<Attachment<?>> all() {
        return Collections.unmodifiableCollection(KEYS.values());
    }
}
