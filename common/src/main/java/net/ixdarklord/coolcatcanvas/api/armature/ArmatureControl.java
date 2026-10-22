package net.ixdarklord.coolcatcanvas.api.armature;

import net.ixdarklord.coolcatcanvas.internal.armature.ArmatureCommandPayload;
import net.ixdarklord.coolcatcore.api.network.Network;
import net.ixdarklord.coolcatcore.internal.mixin.ChunkMapAccessor;
import net.ixdarklord.coolcatcore.internal.mixin.TrackedEntityAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Animating armatures from the server: everyone who can see the entity, block or held item sees the same thing. Each call
 * is one small packet to the players tracking it (and to the entity itself if it is a player); players whose client
 * lacks CoolCatLib are skipped. Called on the client, it acts on that client alone.
 * <pre>{@code
 * ArmatureControl.play(golem, "action", Motion.once("slam"));           // a one-off on a layer
 * ArmatureControl.setVariable(golem, "v.rage", 0.8);                    // read by Molang in the clips
 * ArmatureControl.play(level, pos, Motion.once("open"));                // a block entity, on its base layer
 * ArmatureControl.playHeld(player, InteractionHand.MAIN_HAND, "base", Motion.once("swing"));
 * }</pre>
 * These are moments, not state: someone who comes into range later doesn't see a clip that started before. Keep
 * lasting state (a mob being angry, a chest being open) in synced data, and have a layer's driver play from it.
 */
public final class ArmatureControl {
    /** The layer used when none is named, as on the client's {@code Animator}. */
    public static final String BASE_LAYER = "base";

    private ArmatureControl() {
    }

    // ---- Entities ----

    public static void play(Entity entity, Motion motion) {
        play(entity, BASE_LAYER, motion);
    }

    public static void play(Entity entity, String layer, Motion motion) {
        send(entity, entityCommand(entity, ArmatureCommandPayload.Action.PLAY, layer, motion, 0.0F, "", 0.0));
    }

    /** Blends a layer back to its driver, or to rest, over its motion's fade time. */
    public static void stop(Entity entity, String layer) {
        stop(entity, layer, Motion.DEFAULT_FADE);
    }

    public static void stop(Entity entity, String layer, float fadeSeconds) {
        send(entity, entityCommand(entity, ArmatureCommandPayload.Action.STOP, layer, null, fadeSeconds, "", 0.0));
    }

    /** Sets a Molang variable ({@code "v.name"}) the entity's clips can read. */
    public static void setVariable(Entity entity, String variable, double value) {
        send(entity, entityCommand(entity, ArmatureCommandPayload.Action.VARIABLE, "", null, 0.0F, variable, value));
    }

    // ---- Block entities ----

    public static void play(Level level, BlockPos pos, Motion motion) {
        play(level, pos, BASE_LAYER, motion);
    }

    public static void play(Level level, BlockPos pos, String layer, Motion motion) {
        send(level, pos, blockCommand(pos, ArmatureCommandPayload.Action.PLAY, layer, motion, 0.0F, "", 0.0));
    }

    public static void play(BlockEntity blockEntity, String layer, Motion motion) {
        if (blockEntity.getLevel() != null) play(blockEntity.getLevel(), blockEntity.getBlockPos(), layer, motion);
    }

    public static void stop(Level level, BlockPos pos, String layer, float fadeSeconds) {
        send(level, pos, blockCommand(pos, ArmatureCommandPayload.Action.STOP, layer, null, fadeSeconds, "", 0.0));
    }

    public static void setVariable(Level level, BlockPos pos, String variable, double value) {
        send(level, pos, blockCommand(pos, ArmatureCommandPayload.Action.VARIABLE, "", null, 0.0F, variable, value));
    }

    // ---- Held items ----

    /** Plays on the animator of the stack in an entity's hand (as drawn in that hand, in slots, and in first person). */
    public static void playHeld(LivingEntity entity, InteractionHand hand, String layer, Motion motion) {
        send(entity, new ArmatureCommandPayload(ArmatureCommandPayload.Target.HELD, entity.getId(), BlockPos.ZERO, hand == InteractionHand.OFF_HAND,
                ArmatureCommandPayload.Action.PLAY, layer, motion, 0.0F, "", 0.0));
    }

    public static void setHeldVariable(LivingEntity entity, InteractionHand hand, String variable, double value) {
        send(entity, new ArmatureCommandPayload(ArmatureCommandPayload.Target.HELD, entity.getId(), BlockPos.ZERO, hand == InteractionHand.OFF_HAND,
                ArmatureCommandPayload.Action.VARIABLE, "", null, 0.0F, variable, value));
    }

    // ---- Sending ----

    private static ArmatureCommandPayload entityCommand(Entity entity, ArmatureCommandPayload.Action action, String layer, Motion motion, float fade, String variable, double value) {
        return new ArmatureCommandPayload(ArmatureCommandPayload.Target.ENTITY, entity.getId(), BlockPos.ZERO, false, action, layer, motion, fade, variable, value);
    }

    private static ArmatureCommandPayload blockCommand(BlockPos pos, ArmatureCommandPayload.Action action, String layer, Motion motion, float fade, String variable, double value) {
        return new ArmatureCommandPayload(ArmatureCommandPayload.Target.BLOCK, 0, pos.immutable(), false, action, layer, motion, fade, variable, value);
    }

    private static void send(Entity entity, ArmatureCommandPayload payload) {
        if (entity.level().isClientSide()) {
            ClientSide.run(payload);
            return;
        }
        if (!(entity.level() instanceof ServerLevel level)) return;
        List<ServerPlayer> players = new ArrayList<>();
        Object tracked = ((ChunkMapAccessor) level.getChunkSource().chunkMap).coolcatlib$getEntityMap().get(entity.getId());
        if (tracked != null) {
            for (ServerPlayerConnection connection : ((TrackedEntityAccessor) tracked).coolcatlib$getSeenBy()) players.add(connection.getPlayer());
        }
        if (entity instanceof ServerPlayer self && !players.contains(self)) players.add(self);
        sendTo(players, payload);
    }

    private static void send(Level level, BlockPos pos, ArmatureCommandPayload payload) {
        if (level.isClientSide()) {
            ClientSide.run(payload);
            return;
        }
        if (!(level instanceof ServerLevel server)) return;
        sendTo(server.getChunkSource().chunkMap.getPlayers(ChunkPos.containing(pos), false), payload);
    }

    private static void sendTo(List<ServerPlayer> players, ArmatureCommandPayload payload) {
        for (ServerPlayer player : players) {
            if (Network.canPlayerReceive(player, ArmatureCommandPayload.TYPE)) Network.sendToPlayer(player, payload);
        }
    }

    // Kept apart so a server never loads the client classes it names.
    private static final class ClientSide {
        static void run(ArmatureCommandPayload payload) {
            net.ixdarklord.coolcatcanvas.internal.client.armature.ArmatureClientPackets.handle(payload);
        }
    }
}
