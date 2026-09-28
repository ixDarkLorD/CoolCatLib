package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.attachment.AttachmentSync;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// A chunk reached a player (the synced data of its block entities follows it), or was dropped for them.
@Mixin(PlayerChunkSender.class)
public abstract class PlayerChunkSenderMixin {
    @Inject(method = "sendChunk", at = @At("TAIL"))
    private static void coolcatcore$syncBlockEntities(ServerGamePacketListenerImpl connection, ServerLevel level, LevelChunk chunk, CallbackInfo ci) {
        AttachmentSync.onChunkSent(connection.player, chunk);
    }

    // Only when the client had the chunk: one still pending was never tracked.
    @Inject(method = "dropChunk", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void coolcatcore$stopTracking(ServerPlayer player, ChunkPos pos, CallbackInfo ci) {
        AttachmentSync.onChunkDropped(player, pos);
    }
}
