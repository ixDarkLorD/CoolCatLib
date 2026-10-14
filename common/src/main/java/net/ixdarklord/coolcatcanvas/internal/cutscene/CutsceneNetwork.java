package net.ixdarklord.coolcatcanvas.internal.cutscene;

import net.ixdarklord.coolcatcanvas.api.cutscene.CutsceneEndReason;
import net.ixdarklord.coolcatcanvas.api.event.v2.common.ServerCutsceneEvents;
import net.ixdarklord.coolcatcanvas.internal.client.cutscene.CutsceneManager;
import net.ixdarklord.coolcatcore.api.event.v2.common.PlayerEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.ServerTickEvents;
import net.ixdarklord.coolcatcore.api.network.Network;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// The cutscene packets, and the server's note of which cutscene it asked each player to play. A client's word about
// a cutscene (a keyframe reached, a marker passed, its end) only counts for the one the server started.
public final class CutsceneNetwork {
    private static final Map<UUID, Watch> WATCHING = new HashMap<>();

    // What a player was sent, and for how many server ticks they have been watching it.
    private static final class Watch {
        final Identifier id;
        int ticks;

        Watch(Identifier id) {
            this.id = id;
        }
    }

    private CutsceneNetwork() {}

    public static void init() {
        Network.registerClientbound(CutscenePayload.TYPE, CutscenePayload.STREAM_CODEC, (payload, context) -> CutsceneManager.handle(payload));
        Network.registerServerbound(CutsceneEndedPayload.TYPE, CutsceneEndedPayload.STREAM_CODEC, (payload, context) -> {
            if (context.getPlayer() instanceof ServerPlayer player) ended(player, payload.id(), payload.reason());
        });
        Network.registerServerbound(CutsceneProgressPayload.TYPE, CutsceneProgressPayload.STREAM_CODEC, (payload, context) -> {
            if (context.getPlayer() instanceof ServerPlayer player) progress(player, payload);
        });
        PlayerEvents.LEAVE.register(player -> {
            Watch watch = WATCHING.remove(player.getUUID());
            if (watch != null) ServerCutsceneEvents.ENDED.invoker().onEnded(player, watch.id, CutsceneEndReason.LEFT_WORLD);
        });
        ServerTickEvents.END.register(CutsceneNetwork::tick);
    }

    /** Sends a player a cutscene to play; false when their client can't take it. */
    public static boolean play(ServerPlayer player, Identifier id, CutscenePayload payload) {
        if (!Network.canPlayerReceive(player, CutscenePayload.TYPE)) return false;
        // Whatever they were watching is replaced on their side; the server hears of it from them.
        Network.sendToPlayer(player, payload);
        Watch old = WATCHING.put(player.getUUID(), new Watch(id));
        if (old != null) ServerCutsceneEvents.ENDED.invoker().onEnded(player, old.id, CutsceneEndReason.REPLACED);
        ServerCutsceneEvents.STARTED.invoker().onStarted(player, id);
        return true;
    }

    public static void stop(ServerPlayer player) {
        if (Network.canPlayerReceive(player, CutscenePayload.TYPE)) Network.sendToPlayer(player, CutscenePayload.stop());
    }

    public static @Nullable Identifier watching(ServerPlayer player) {
        Watch watch = WATCHING.get(player.getUUID());
        return watch != null ? watch.id : null;
    }

    private static void tick(MinecraftServer server) {
        if (WATCHING.isEmpty()) return;
        // A listener may start or stop cutscenes, so the watches are walked from a copy.
        for (Map.Entry<UUID, Watch> entry : List.copyOf(WATCHING.entrySet())) {
            Watch watch = entry.getValue();
            watch.ticks++;
            if (!ServerCutsceneEvents.TICK.hasListeners()) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null && WATCHING.get(entry.getKey()) == watch) ServerCutsceneEvents.TICK.invoker().onTick(player, watch.id, watch.ticks);
        }
    }

    private static void progress(ServerPlayer player, CutsceneProgressPayload payload) {
        Watch watch = WATCHING.get(player.getUUID());
        if (watch == null || !watch.id.equals(payload.id())) return;
        if (payload.marker().isPresent()) ServerCutsceneEvents.MARKER.invoker().onMarker(player, watch.id, payload.marker().get());
        else if (payload.keyframe() >= 0) ServerCutsceneEvents.KEYFRAME.invoker().onKeyframe(player, watch.id, payload.keyframe());
    }

    private static void ended(ServerPlayer player, Identifier id, CutsceneEndReason reason) {
        // A replaced cutscene was already reported when its replacement was sent.
        Watch watch = WATCHING.get(player.getUUID());
        if (reason == CutsceneEndReason.REPLACED || watch == null || !watch.id.equals(id)) return;
        WATCHING.remove(player.getUUID());
        ServerCutsceneEvents.ENDED.invoker().onEnded(player, id, reason);
    }
}
