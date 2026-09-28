package net.ixdarklord.coolcatcore.mixin;

import net.ixdarklord.coolcatcore.api.event.v2.common.BlockEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// BlockEvents.PLACED (Fabric has no block place event): after a block item put its block in the level.
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "placeBlock", at = @At("RETURN"))
    private void coolcatcore$placed(BlockPlaceContext context, BlockState placementState, CallbackInfoReturnable<Boolean> cir) {
        Level level = context.getLevel();
        if (!cir.getReturnValueZ() || level.isClientSide()) return;
        BlockPos pos = context.getClickedPos();
        BlockEvents.PLACED.invoker().onPlaced(level, pos, level.getBlockState(pos), context.getPlayer());
    }
}
