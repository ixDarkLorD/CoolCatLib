package net.ixdarklord.coolcatcore.internal.mixin.forge;

import net.ixdarklord.coolcatcore.internal.event.BlockDrops;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// BlockEvents.DROPS: Forge drops a broken block's experience after its items, so they're held until the breaking is done
// (BlockDrops).
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {
    @Shadow
    protected ServerLevel level;
    @Unique
    private BlockPos coolcatlib$outer;

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void coolcatlib$breaking(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        this.coolcatlib$outer = BlockDrops.breaking(pos);
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void coolcatlib$broken(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockDrops.broken(this.level, pos, this.coolcatlib$outer);
    }
}
