package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.attachment.AttachmentSync;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// A chunk reached a player (the synced data of its block entities follows it), or was dropped for them.
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
    @Inject(method = "playerLoadedChunk", at = @At("TAIL"))
    private void coolcatcore$syncBlockEntities(ServerPlayer player, MutableObject<ClientboundLevelChunkWithLightPacket> packetCache, LevelChunk chunk, CallbackInfo ci) {
        AttachmentSync.onChunkSent(player, chunk);
    }

    // Only when the client had the chunk: updateChunkTracking forgets it only then.
    @Inject(method = "updateChunkTracking", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;untrackChunk(Lnet/minecraft/world/level/ChunkPos;)V"))
    private void coolcatcore$stopTracking(ServerPlayer player, ChunkPos pos, MutableObject<ClientboundLevelChunkWithLightPacket> packetCache,
                                          boolean wasLoaded, boolean load, CallbackInfo ci) {
        AttachmentSync.onChunkDropped(player, pos);
    }
}
