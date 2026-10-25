package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.api.event.v2.common.PlayerEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// PlayerEvents.CRAFTED: what was crafted was taken out of the result slot.
@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin {
    @Shadow
    @Final
    private Player player;
    @Shadow
    @Final
    private CraftingContainer craftSlots;

    @Inject(method = "checkTakeAchievements", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/world/item/ItemStack;onCraftedBy(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;I)V"))
    private void coolcatlib$crafted(ItemStack carried, CallbackInfo ci) {
        if (!this.player.level().isClientSide() && PlayerEvents.CRAFTED.hasListeners()) PlayerEvents.CRAFTED.invoker().onCrafted(this.player, carried, this.craftSlots);
    }
}
