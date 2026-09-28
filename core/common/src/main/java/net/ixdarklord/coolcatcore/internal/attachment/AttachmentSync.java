package net.ixdarklord.coolcatcore.internal.attachment;

import io.netty.buffer.Unpooled;
import net.ixdarklord.coolcatcore.api.event.v2.common.AttachmentEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.PlayerEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.ServerLifecycleEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.ServerTickEvents;
import net.ixdarklord.coolcatcore.api.attachment.Attachment;
import net.ixdarklord.coolcatcore.api.network.Network;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.ixdarklord.coolcatcore.internal.mixin.ChunkMapAccessor;
import net.ixdarklord.coolcatcore.internal.mixin.TrackedEntityAccessor;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Sends Attachment values to the players who see their holder: changed values once per server tick; everything when a
// player starts tracking an entity, receives a chunk, joins, respawns or changes dimension.
public final class AttachmentSync {
    // Clientbound custom payloads are capped at 1 MiB.
    private static final int MAX_PAYLOAD_BYTES = 1_000_000;
    private static final Set<AttachmentStore> PENDING = new LinkedHashSet<>();
    // The player object and level each online player had at the last flush; a new one means the client rebuilt its
    // player (respawn, dimension change) or just joined.
    private static final Map<UUID, Seen> SEEN = new HashMap<>();

    private AttachmentSync() {}

    private record Seen(ServerPlayer player, Level level) {}

    private record Encoded(Attachment<?> key, AttachmentSyncPayload.Entry entry, int size) {}

    public static void init() {
        Network.registerClientbound(AttachmentSyncPayload.TYPE, AttachmentSyncPayload.STREAM_CODEC, (payload, context) -> context.queue(() -> {
            if (context.getPlayer() != null) receive(payload, context.getPlayer());
        }));
        ServerTickEvents.END.register(AttachmentSync::flush);
        PlayerEvents.LEAVE.register(player -> stopTracking(((AttachmentStoreAccess) player).coolcatcore$getOrCreateAttachments(), player));
        ServerLifecycleEvents.STOPPED.register(server -> {
            PENDING.clear();
            SEEN.clear();
        });
    }

    static void queue(AttachmentStore store, Attachment<?> key) {
        if (!(store.level() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();
        if (!server.isSameThread()) {
            server.execute(() -> queue(store, key));
            return;
        }
        store.addDirty(key);
        PENDING.add(store);
    }

    private static void flush(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Seen seen = SEEN.get(player.getUUID());
            if (seen != null && seen.player == player && seen.level == player.level()) continue;
            SEEN.put(player.getUUID(), new Seen(player, player.level()));
            // Every player tracks their own attachments, whether or not they carry any yet.
            AttachmentStore store = ((AttachmentStoreAccess) player).coolcatcore$getOrCreateAttachments();
            startTracking(store, player);
            sendFull(store, player);
        }
        SEEN.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);

        if (PENDING.isEmpty()) return;
        List<AttachmentStore> stores = new ArrayList<>(PENDING);
        PENDING.clear();
        for (AttachmentStore store : stores) {
            List<Attachment<?>> keys = store.takeDirty();
            if (keys.isEmpty() || !store.isValid() || !(store.level() instanceof ServerLevel level)) continue;
            send(store, keys, recipients(store, level));
        }
    }

    /** The entity started being tracked by the player. */
    public static void onStartTracking(Entity entity, ServerPlayer player) {
        AttachmentStore store = ((AttachmentStoreAccess) entity).coolcatcore$getAttachments();
        if (store == null) return;
        startTracking(store, player);
        sendFull(store, player);
    }

    /** The entity stopped being tracked by the player. */
    public static void onStopTracking(Entity entity, ServerPlayer player) {
        stopTracking(((AttachmentStoreAccess) entity).coolcatcore$getAttachments(), player);
    }

    /** A chunk was sent to the player; its block entities follow. */
    public static void onChunkSent(ServerPlayer player, LevelChunk chunk) {
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            AttachmentStore store = ((AttachmentStoreAccess) blockEntity).coolcatcore$getAttachments();
            if (store == null) continue;
            startTracking(store, player);
            sendFull(store, player);
        }
    }

    /** The player's client forgets a chunk. */
    public static void onChunkDropped(ServerPlayer player, ChunkPos pos) {
        if (!AttachmentEvents.STOP_TRACKING.hasListeners()) return;
        LevelChunk chunk = player.level().getChunkSource().getChunkNow(pos.x(), pos.z());
        if (chunk == null) return;
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            stopTracking(((AttachmentStoreAccess) blockEntity).coolcatcore$getAttachments(), player);
        }
    }

    private static void startTracking(AttachmentStore store, ServerPlayer player) {
        if (AttachmentEvents.START_TRACKING.hasListeners()) AttachmentEvents.START_TRACKING.invoker().onStartTracking(store, player);
    }

    private static void stopTracking(@Nullable AttachmentStore store, ServerPlayer player) {
        if (store != null && AttachmentEvents.STOP_TRACKING.hasListeners()) AttachmentEvents.STOP_TRACKING.invoker().onStopTracking(store, player);
    }

    private static void sendFull(AttachmentStore store, ServerPlayer player) {
        List<Attachment<?>> keys = store.syncedKeys();
        if (!keys.isEmpty()) send(store, keys, List.of(player));
    }

    private static void send(AttachmentStore store, List<Attachment<?>> keys, List<ServerPlayer> players) {
        if (players.isEmpty() || !(store.level() instanceof ServerLevel level)) return;
        Object owner = store.owner();
        List<Encoded> encoded = new ArrayList<>(keys.size());
        for (Attachment<?> key : keys) {
            Encoded value = encode(store, key, level.registryAccess());
            if (value != null) encoded.add(value);
        }
        for (ServerPlayer player : players) {
            List<AttachmentSyncPayload.Entry> entries = new ArrayList<>(encoded.size());
            int size = 0;
            for (Encoded value : encoded) {
                if (!value.key.syncPolicy().shouldSync(owner, player)) continue;
                entries.add(value.entry);
                size += value.size;
            }
            if (entries.isEmpty()) continue;
            if (size > MAX_PAYLOAD_BYTES) {
                CoolCatCore.LOGGER.error("The synced data of {} is too large to send ({} bytes)", owner, size);
                continue;
            }
            Network.sendToPlayer(player, owner instanceof Entity entity
                    ? AttachmentSyncPayload.entity(entity.getId(), entries)
                    : AttachmentSyncPayload.block(((BlockEntity) owner).getBlockPos(), entries));
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> @Nullable Encoded encode(AttachmentStore store, Attachment<T> key, RegistryAccess registries) {
        T value = (T) store.rawValue(key);
        if (value == null) return new Encoded(key, new AttachmentSyncPayload.Entry(key.id(), null), 0);
        StreamCodec<? super RegistryFriendlyByteBuf, T> codec = key.streamCodec();
        if (codec == null) return null;
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            codec.encode(buf, value);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new Encoded(key, new AttachmentSyncPayload.Entry(key.id(), bytes), bytes.length);
        } catch (RuntimeException e) {
            CoolCatCore.LOGGER.error("Couldn't encode {} of {} for syncing", key.id(), store.owner(), e);
            return null;
        } finally {
            buf.release();
        }
    }

    private static List<ServerPlayer> recipients(AttachmentStore store, ServerLevel level) {
        List<ServerPlayer> players = new ArrayList<>();
        if (store.owner() instanceof Entity entity) {
            Object tracked = ((ChunkMapAccessor) level.getChunkSource().chunkMap).coolcatcore$getEntityMap().get(entity.getId());
            if (tracked != null) {
                for (ServerPlayerConnection connection : ((TrackedEntityAccessor) tracked).coolcatcore$getSeenBy()) {
                    players.add(connection.getPlayer());
                }
            }
            if (entity instanceof ServerPlayer self && !players.contains(self)) players.add(self);
        } else {
            BlockEntity blockEntity = (BlockEntity) store.owner();
            players.addAll(level.getChunkSource().chunkMap.getPlayers(ChunkPos.containing(blockEntity.getBlockPos()), false));
        }
        return players;
    }

    // --- Client ---

    private static void receive(AttachmentSyncPayload payload, Player player) {
        Level level = player.level();
        Object owner = payload.pos() != null ? level.getBlockEntity(payload.pos()) : level.getEntity(payload.entityId());
        if (owner == null) return;
        AttachmentStore store = ((AttachmentStoreAccess) owner).coolcatcore$getOrCreateAttachments();
        Set<Attachment<?>> applied = new LinkedHashSet<>();
        for (AttachmentSyncPayload.Entry entry : payload.entries()) {
            Attachment<?> key = AttachmentIndex.get(entry.key());
            if (key == null || key.streamCodec() == null) {
                CoolCatCore.LOGGER.debug("Skipping synced data {} this client doesn't know", entry.key());
                continue;
            }
            if (apply(store, key, entry.value(), level.registryAccess())) applied.add(key);
        }
        if (!applied.isEmpty() && AttachmentEvents.RECEIVED.hasListeners()) {
            AttachmentEvents.RECEIVED.invoker().onReceived(store, Collections.unmodifiableSet(applied));
        }
    }

    private static <T> boolean apply(AttachmentStore store, Attachment<T> key, byte @Nullable [] bytes, RegistryAccess registries) {
        if (bytes == null) {
            store.applySynced(key, null);
            return true;
        }
        StreamCodec<? super RegistryFriendlyByteBuf, T> codec = key.streamCodec();
        if (codec == null) return false;
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), registries);
        try {
            store.applySynced(key, codec.decode(buf));
            return true;
        } catch (RuntimeException e) {
            CoolCatCore.LOGGER.error("Couldn't read synced {} of {}", key.id(), store.owner(), e);
            return false;
        } finally {
            buf.release();
        }
    }
}
