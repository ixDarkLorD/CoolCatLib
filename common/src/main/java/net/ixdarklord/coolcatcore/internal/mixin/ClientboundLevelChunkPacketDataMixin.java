package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.block.BlockViewRegistry;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Iterator;
import java.util.List;

// BlockViews: a chunk built for a player leaves out the block entities of blocks that look like others to them (the
// list follows the chunk's block entities, one for one).
@Mixin(ClientboundLevelChunkPacketData.class)
public abstract class ClientboundLevelChunkPacketDataMixin {
    @Shadow
    @Final
    private List<?> blockEntitiesData; // (of BlockEntityInfo, which is private)

    @Inject(method = "<init>(Lnet/minecraft/world/level/chunk/LevelChunk;)V", at = @At("RETURN"))
    private void coolcatlib$hideBlockEntities(LevelChunk chunk, CallbackInfo ci) {
        BlockViewRegistry.Look look = BlockViewRegistry.buildingFor();
        if (look == null) return;
        Iterator<?> infos = this.blockEntitiesData.iterator();
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (!infos.hasNext()) break;
            infos.next();
            if (BlockViewRegistry.hides(look, blockEntity.getBlockState())) infos.remove();
        }
    }
}
