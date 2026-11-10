package net.ixdarklord.coolcatcore.mixin;

import net.ixdarklord.coolcatcore.internal.event.MiningContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// PlayerEvents.HARVEST_CHECK: where the block a player broke is, while the game asks whether it drops (MiningContext).
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {
    @Unique
    private BlockPos coolcatlib$outer;

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void coolcatlib$enter(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        this.coolcatlib$outer = MiningContext.enter(pos);
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void coolcatlib$exit(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        MiningContext.exit(this.coolcatlib$outer);
    }
}
