package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.attachment.AttachmentSync;
import net.ixdarklord.coolcatcore.internal.block.BlockViewRegistry;
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
    // BlockViews: the chunk is built for this player as its blocks look to them; one built that way isn't kept for the
    // next player (the game builds a chunk once for all the players it reaches at the same time).
    @Inject(method = "playerLoadedChunk", at = @At("HEAD"))
    private void coolcatlib$buildFor(ServerPlayer player, MutableObject<ClientboundLevelChunkWithLightPacket> packetCache, LevelChunk chunk, CallbackInfo ci) {
        BlockViewRegistry.Look look = BlockViewRegistry.lookFor(player);
        if (look != null) packetCache.setValue(null);
        BlockViewRegistry.buildingFor(look);
    }

    @Inject(method = "playerLoadedChunk", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/server/level/ServerPlayer;trackChunk(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/network/protocol/Packet;)V"))
    private void coolcatlib$built(ServerPlayer player, MutableObject<ClientboundLevelChunkWithLightPacket> packetCache, LevelChunk chunk, CallbackInfo ci) {
        if (BlockViewRegistry.buildingFor() != null) packetCache.setValue(null);
        BlockViewRegistry.buildingFor(null);
    }

    @Inject(method = "playerLoadedChunk", at = @At("TAIL"))
    private void coolcatlib$syncBlockEntities(ServerPlayer player, MutableObject<ClientboundLevelChunkWithLightPacket> packetCache, LevelChunk chunk, CallbackInfo ci) {
        AttachmentSync.onChunkSent(player, chunk);
    }

    // Only when the client had the chunk: updateChunkTracking forgets it only then.
    @Inject(method = "updateChunkTracking", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;untrackChunk(Lnet/minecraft/world/level/ChunkPos;)V"))
    private void coolcatlib$stopTracking(ServerPlayer player, ChunkPos pos, MutableObject<ClientboundLevelChunkWithLightPacket> packetCache,
                                          boolean wasLoaded, boolean load, CallbackInfo ci) {
        AttachmentSync.onChunkDropped(player, pos);
    }
}
