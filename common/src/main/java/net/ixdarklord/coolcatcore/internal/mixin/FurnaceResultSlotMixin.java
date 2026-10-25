package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.api.event.v2.common.PlayerEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// PlayerEvents.SMELTED: what was smelted was taken out of a furnace.
@Mixin(FurnaceResultSlot.class)
public abstract class FurnaceResultSlotMixin {
    @Shadow
    @Final
    private Player player;

    @Inject(method = "checkTakeAchievements", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/world/item/ItemStack;onCraftedBy(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;I)V"))
    private void coolcatlib$smelted(ItemStack carried, CallbackInfo ci) {
        if (!this.player.level().isClientSide() && PlayerEvents.SMELTED.hasListeners()) PlayerEvents.SMELTED.invoker().onSmelted(this.player, carried, ((Slot) (Object) this).container);
    }
}
