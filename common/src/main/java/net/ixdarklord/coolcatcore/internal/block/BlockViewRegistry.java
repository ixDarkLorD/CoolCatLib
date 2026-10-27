package net.ixdarklord.coolcatcore.internal.block;

import io.netty.buffer.Unpooled;
import net.ixdarklord.coolcatcore.api.block.BlockView;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BlockViews: which blocks look like what to a player ({@link Look}, one for each set of keys), the chunk sections as
 * they look to them (made once for each look, until the section changes), and what's sent to them changed to match:
 * chunks as they're built for a player (ChunkMapMixin, LevelChunkSectionMixin, ClientboundLevelChunkPacketDataMixin),
 * and block changes and block entities as they're sent (ServerGamePacketListenerImplMixin).
 */
public final class BlockViewRegistry {
    private static final Map<ResourceLocation, BlockView> VIEWS = new LinkedHashMap<>();
    private static volatile BlockView[] views = new BlockView[0];
    private static final Map<List<Object>, Map<BlockState, BlockState>> LOOKS = new ConcurrentHashMap<>();
    private static final Map<LevelChunkSection, SectionLooks> SECTIONS = new WeakHashMap<>();
    private static final ThreadLocal<Look> BUILDING_FOR = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SENDING = new ThreadLocal<>();

    private BlockViewRegistry() {}

    public static synchronized void register(ResourceLocation id, BlockView view) {
        if (VIEWS.putIfAbsent(id, view) != null) throw new IllegalArgumentException("A block view is already registered as " + id);
        views = VIEWS.values().toArray(BlockView[]::new);
        LOOKS.clear();
    }

    /** How blocks look to a player, or null when every view leaves them as they are. */
    public static @Nullable Look lookFor(ServerPlayer player) {
        BlockView[] all = views;
        if (all.length == 0) return null;
        List<Object> keys = null;
        for (int i = 0; i < all.length; i++) {
            Object key = all[i].key(player);
            if (key == null) continue;
            if (keys == null) {
                keys = new ArrayList<>(all.length * 2);
            }
            keys.add(i);
            keys.add(key);
        }
        return keys == null ? null : new Look(all, List.copyOf(keys));
    }

    // ---- Chunks, as they're built for a player ----

    public static void buildingFor(@Nullable Look look) {
        if (look == null) BUILDING_FOR.remove();
        else BUILDING_FOR.set(look);
    }

    public static @Nullable Look buildingFor() {
        return BUILDING_FOR.get();
    }

    /** A section's blocks as they look while a chunk is built for a player: the section's own, if nothing changes. */
    public static PalettedContainer<BlockState> statesOf(LevelChunkSection section, PalettedContainer<BlockState> states) {
        Look look = BUILDING_FOR.get();
        if (look == null) return states;
        synchronized (SECTIONS) {
            SectionLooks looks = SECTIONS.computeIfAbsent(section, s -> new SectionLooks());
            int changes = ((Tracked) section).coolcatlib$getChanges();
            if (looks.changes != changes) {
                looks.byLook.clear();
                looks.changes = changes;
            }
            return looks.byLook.computeIfAbsent(look.keys, k -> viewed(states, look));
        }
    }

    private static PalettedContainer<BlockState> viewed(PalettedContainer<BlockState> states, Look look) {
        if (!states.maybeHas(state -> look.view(state) != state)) return states;
        PalettedContainer<BlockState> copy = states.copy();
        Map<BlockState, BlockState> seen = new IdentityHashMap<>();
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockState state = copy.get(x, y, z);
                    BlockState shown = seen.computeIfAbsent(state, look::view);
                    if (shown != state) copy.getAndSetUnchecked(x, y, z, shown);
                }
            }
        }
        return copy;
    }

    /** Whether a block entity is left out of what a player is sent: its block looks like another to them. */
    public static boolean hides(Look look, BlockState state) {
        return look.view(state) != state;
    }

    // ---- Block changes and block entities, as they're sent ----

    /** The packet a player is sent instead (null to send nothing), changed to how blocks look to them. */
    public static @Nullable Packet<?> forPlayer(ServerPlayer player, Packet<?> packet) {
        if (views.length == 0 || SENDING.get() != null) return packet;
        if (!(packet instanceof ClientboundBlockUpdatePacket) && !(packet instanceof ClientboundSectionBlocksUpdatePacket)
                && !(packet instanceof ClientboundBlockEntityDataPacket) && !(packet instanceof ClientboundBundlePacket)) {
            return packet;
        }
        Look look = lookFor(player);
        return look == null ? packet : viewed(player, look, packet);
    }

    @SuppressWarnings("unchecked")
    private static @Nullable Packet<?> viewed(ServerPlayer player, Look look, Packet<?> packet) {
        if (packet instanceof ClientboundBlockUpdatePacket update) {
            BlockState shown = look.view(update.getBlockState());
            return shown == update.getBlockState() ? packet : new ClientboundBlockUpdatePacket(update.getPos(), shown);
        }
        if (packet instanceof ClientboundSectionBlocksUpdatePacket updates) {
            BlockState[] states = ((SectionUpdate) updates).coolcatlib$getStates();
            boolean changed = false;
            for (BlockState state : states) {
                if (look.view(state) != state) {
                    changed = true;
                    break;
                }
            }
            if (!changed) return packet;
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                updates.write(buffer);
                ClientboundSectionBlocksUpdatePacket copy = new ClientboundSectionBlocksUpdatePacket(buffer);
                BlockState[] shown = ((SectionUpdate) copy).coolcatlib$getStates();
                for (int i = 0; i < shown.length; i++) shown[i] = look.view(shown[i]);
                return copy;
            } finally {
                buffer.release();
            }
        }
        if (packet instanceof ClientboundBlockEntityDataPacket data) {
            BlockPos pos = data.getPos();
            ServerLevel level = player.serverLevel();
            if (!level.isLoaded(pos)) return packet;
            return hides(look, level.getBlockState(pos)) ? null : packet;
        }
        if (packet instanceof ClientboundBundlePacket bundle) {
            List<Packet<ClientGamePacketListener>> parts = new ArrayList<>();
            boolean changed = false;
            for (Packet<ClientGamePacketListener> part : bundle.subPackets()) {
                Packet<?> shown = viewed(player, look, part);
                if (shown != part) changed = true;
                if (shown != null) parts.add((Packet<ClientGamePacketListener>) shown);
            }
            return changed ? new ClientboundBundlePacket(parts) : packet;
        }
        return packet;
    }

    /** Sends a packet without it being changed again. */
    public static void sendAsIs(Runnable send) {
        SENDING.set(Boolean.TRUE);
        try {
            send.run();
        } finally {
            SENDING.remove();
        }
    }

    // ---- Refreshing ----

    public static void refresh(ServerPlayer player, @Nullable Collection<ChunkPos> only) {
        LOOKS.clear();
        Set<ChunkPos> wanted = only == null ? null : new HashSet<>(only);
        ServerLevel level = player.serverLevel();
        ChunkPos center = player.chunkPosition();
        int range = level.getServer().getPlayerList().getViewDistance() + 1;
        for (int x = center.x - range; x <= center.x + range; x++) {
            for (int z = center.z - range; z <= center.z + range; z++) {
                ChunkPos pos = new ChunkPos(x, z);
                if (wanted != null && !wanted.contains(pos)) continue;
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null || !level.getChunkSource().chunkMap.getPlayers(pos, false).contains(player)) continue;
                buildingFor(lookFor(player));
                try {
                    player.connection.send(new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null));
                } finally {
                    buildingFor(null);
                }
            }
        }
    }

    /** What blocks look like to the players with one set of keys. */
    public static final class Look {
        private final BlockView[] views;
        private final List<Object> keys;
        private final Map<BlockState, BlockState> shown;

        private Look(BlockView[] views, List<Object> keys) {
            this.views = views;
            this.keys = keys;
            this.shown = LOOKS.computeIfAbsent(keys, k -> new ConcurrentHashMap<>());
        }

        public BlockState view(BlockState state) {
            BlockState known = this.shown.get(state);
            if (known != null) return known;
            BlockState shown = state;
            for (int i = 0; i < this.keys.size(); i += 2) shown = this.views[(Integer) this.keys.get(i)].view(this.keys.get(i + 1), shown);
            this.shown.put(state, shown);
            return shown;
        }
    }

    private static final class SectionLooks {
        private int changes = -1;
        private final Map<List<Object>, PalettedContainer<BlockState>> byLook = new LinkedHashMap<>(4, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<List<Object>, PalettedContainer<BlockState>> eldest) {
                return this.size() > 8;
            }
        };
    }

    /** Implemented by LevelChunkSection: how many times its blocks changed. */
    public interface Tracked {
        int coolcatlib$getChanges();
    }

    /** Implemented by ClientboundSectionBlocksUpdatePacket. */
    public interface SectionUpdate {
        BlockState[] coolcatlib$getStates();
    }
}
