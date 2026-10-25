package net.ixdarklord.coolcatcore.internal.attachment;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.jetbrains.annotations.Nullable;

// The stores of levels and of the server. A server level's and the server's live in saved data, made the first time a
// value is set; a client level's in the level itself (LevelMixin); the client's copy of the server's values in CLIENT,
// emptied when the player leaves.
public final class ServerAttachments {
    /** The owner of the client's copy of the server's values. */
    public static final Object CLIENT_OWNER = new Object() {
        @Override
        public String toString() {
            return "the server (client copy)";
        }
    };
    private static AttachmentStore client = new AttachmentStore(CLIENT_OWNER);

    private ServerAttachments() {}

    /** Mixed into the server: its player data, to read and write offline players' values. */
    public interface PlayerDataAccess {
        PlayerDataStorage coolcatlib$playerData();
    }

    /** A server level's store, made if needed. */
    public static AttachmentStore level(ServerLevel level, @Nullable AttachmentStore current) {
        if (current != null) return current;
        return level.getDataStorage().computeIfAbsent(AttachmentSavedData.LEVEL).bind(new AttachmentStore(level), level.registryAccess());
    }

    /** A server level's store if it has one, saved or in use. */
    public static @Nullable AttachmentStore existingLevel(ServerLevel level, @Nullable AttachmentStore current) {
        if (current != null) return current;
        AttachmentSavedData saved = level.getDataStorage().get(AttachmentSavedData.LEVEL);
        return saved == null ? null : saved.bind(new AttachmentStore(level), level.registryAccess());
    }

    /** The server's store, made if needed. */
    public static AttachmentStore server(MinecraftServer server, @Nullable AttachmentStore current) {
        if (current != null) return current;
        return server.getDataStorage().computeIfAbsent(AttachmentSavedData.SERVER).bind(new AttachmentStore(server), server.registryAccess());
    }

    /** The server's store if it has one, saved or in use. */
    public static @Nullable AttachmentStore existingServer(MinecraftServer server, @Nullable AttachmentStore current) {
        if (current != null) return current;
        AttachmentSavedData saved = server.getDataStorage().get(AttachmentSavedData.SERVER);
        return saved == null ? null : saved.bind(new AttachmentStore(server), server.registryAccess());
    }

    /** The server's values as this side sees them: the server's own on a server level, the synced copy on a client. */
    public static AttachmentStore global(Level level) {
        return level instanceof ServerLevel serverLevel ? (AttachmentStore) ((AttachmentStoreAccess) serverLevel.getServer()).coolcatlib$getOrCreateAttachments() : client;
    }

    /** The client's copy of the server's values. */
    public static AttachmentStore client() {
        return client;
    }

    /** The client left the server: what it sent is forgotten. */
    public static void clearClient() {
        client = new AttachmentStore(CLIENT_OWNER);
    }
}
