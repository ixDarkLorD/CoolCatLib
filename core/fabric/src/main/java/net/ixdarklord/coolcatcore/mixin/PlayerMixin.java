package net.ixdarklord.coolcatcore.mixin;

import net.ixdarklord.coolcatcore.api.event.v2.common.PlayerEvents;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// PlayerEvents.START_TICK / END_TICK (Fabric has no player tick event).
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void coolcatcore$startTick(CallbackInfo ci) {
        PlayerEvents.START_TICK.invoker().onStartTick((Player) (Object) this);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void coolcatcore$endTick(CallbackInfo ci) {
        PlayerEvents.END_TICK.invoker().onEndTick((Player) (Object) this);
    }
}
