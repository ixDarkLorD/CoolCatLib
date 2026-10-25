package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.api.event.v2.common.PlayerEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// PlayerEvents.PICKUP_ITEM: once the item may be picked up (its delay is over, it's for this player), before it goes
// into the inventory.
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Inject(method = "playerTouch", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"), cancellable = true)
    private void coolcatlib$beforePickup(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer && PlayerEvents.PICKUP_ITEM.hasListeners()
                && PlayerEvents.PICKUP_ITEM.invoker().onPickupItem(serverPlayer, (ItemEntity) (Object) this).isInterrupt()) {
            ci.cancel();
        }
    }
}
