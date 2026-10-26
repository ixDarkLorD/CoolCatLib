package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.api.event.v2.common.BlockEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// BlockEvents.PLACE for an enderman setting down the block it carries: a place it may not use is one it can't, so it
// keeps carrying the block.
@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanLeaveBlockGoal")
public abstract class EndermanLeaveBlockGoalMixin {
    @Shadow
    @Final
    private EnderMan enderman;

    @Inject(method = "canPlaceBlock", at = @At("RETURN"), cancellable = true)
    private void coolcatlib$mayPlace(Level level, BlockPos pos, BlockState carried, BlockState targetState, BlockState belowState, BlockPos below, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() || level.isClientSide() || !BlockEvents.PLACE.hasListeners()) return;
        if (BlockEvents.PLACE.invoker().onPlace(level, pos, carried, this.enderman, ItemStack.EMPTY).isInterrupt()) cir.setReturnValue(false);
    }
}
