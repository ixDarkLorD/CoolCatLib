package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.api.event.v2.common.PlayerEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// PlayerEvents.ADVANCEMENT_EARNED, once an advancement is done and its rewards given.
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
    @Shadow
    private ServerPlayer player;

    @Inject(method = "award", at = @At(value = "INVOKE", target = "Lnet/minecraft/advancements/AdvancementRewards;grant(Lnet/minecraft/server/level/ServerPlayer;)V", shift = At.Shift.AFTER))
    private void coolcatlib$earned(AdvancementHolder advancement, String criterion, CallbackInfoReturnable<Boolean> cir) {
        if (PlayerEvents.ADVANCEMENT_EARNED.hasListeners()) PlayerEvents.ADVANCEMENT_EARNED.invoker().onAdvancementEarned(this.player, advancement);
    }
}
