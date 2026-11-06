package net.ixdarklord.coolcatcore.mixin;

import net.ixdarklord.coolcatcore.internal.event.MiningContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// PlayerEvents.BREAK_SPEED / HARVEST_CHECK: where the block being broken is, while its breaking speed is worked out
// (MiningContext).
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {
    @Unique
    private static final ThreadLocal<BlockPos> COOLCATLIB$OUTER = new ThreadLocal<>();

    @Inject(method = "getDestroyProgress", at = @At("HEAD"))
    private void coolcatlib$enter(Player player, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        COOLCATLIB$OUTER.set(MiningContext.enter(pos));
    }

    @Inject(method = "getDestroyProgress", at = @At("RETURN"))
    private void coolcatlib$exit(Player player, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        MiningContext.exit(COOLCATLIB$OUTER.get());
    }
}
