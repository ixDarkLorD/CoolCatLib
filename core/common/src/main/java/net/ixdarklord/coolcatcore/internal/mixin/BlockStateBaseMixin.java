package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.api.block.ExtendedBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// A block with an ExtendedBlockEntity is being replaced by another block: its preRemoveSideEffects runs first, as
// BlockEntity.preRemoveSideEffects does on 26.1 (dropping a container's contents, for instance).
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {
    @Shadow
    protected abstract BlockState asState();

    @Inject(method = "onRemove", at = @At("HEAD"))
    private void coolcatcore$preRemoveSideEffects(Level level, BlockPos pos, BlockState newState, boolean movedByPiston, CallbackInfo ci) {
        BlockState state = this.asState();
        if (level.isClientSide() || !state.hasBlockEntity() || state.is(newState.getBlock())) return;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof ExtendedBlockEntity extended) extended.preRemoveSideEffects(pos, state);
    }
}
