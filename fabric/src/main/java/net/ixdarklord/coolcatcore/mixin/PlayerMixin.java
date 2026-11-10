package net.ixdarklord.coolcatcore.mixin;

import net.ixdarklord.coolcatcore.api.event.v2.common.PlayerEvents;
import net.ixdarklord.coolcatcore.internal.event.MiningContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// PlayerEvents.START_TICK / END_TICK, BREAK_SPEED and HARVEST_CHECK (Fabric has no events for these).
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void coolcatlib$startTick(CallbackInfo ci) {
        PlayerEvents.START_TICK.invoker().onStartTick((Player) (Object) this);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void coolcatlib$endTick(CallbackInfo ci) {
        PlayerEvents.END_TICK.invoker().onEndTick((Player) (Object) this);
    }

    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
    private void coolcatlib$breakSpeed(BlockState state, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(MiningContext.breakSpeed((Player) (Object) this, state, cir.getReturnValueF()));
    }

    @Inject(method = "hasCorrectToolForDrops", at = @At("RETURN"), cancellable = true)
    private void coolcatlib$harvestCheck(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(MiningContext.canHarvest((Player) (Object) this, state, cir.getReturnValueZ()));
    }
}
