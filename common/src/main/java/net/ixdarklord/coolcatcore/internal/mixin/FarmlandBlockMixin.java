package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.api.event.v2.common.BlockEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// BlockEvents.TRAMPLE: farmland turned back to its base block by something landing on it (with no entity, it's drying
// out, which isn't a trample).
@Mixin(FarmlandBlock.class)
public abstract class FarmlandBlockMixin {
    @Inject(method = "turnToBaseBlock", at = @At("HEAD"), cancellable = true)
    private void coolcatlib$trample(@Nullable Entity entity, BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
        if (entity == null || !(level instanceof ServerLevel serverLevel) || !BlockEvents.TRAMPLE.hasListeners()) return;
        if (BlockEvents.TRAMPLE.invoker().onTrample(serverLevel, pos, state, entity).isInterrupt()) ci.cancel();
    }
}
