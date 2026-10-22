package net.ixdarklord.coolcatcore.internal.mixin.client;

import net.ixdarklord.coolcatcore.api.client.item.HeldItemSwaps;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// HeldItemSwaps: a quiet change to the held stack replaces the one shown at once, so no swap plays for it.
@Mixin(FirstPersonHandsAndItems.class)
public abstract class ItemInHandRendererMixin {
    @Inject(method = "shouldInstantlyReplaceVisibleItem", at = @At("HEAD"), cancellable = true)
    private void coolcatcore$quietSwap(ItemStack shown, ItemStack next, LocalPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (HeldItemSwaps.isQuiet(shown, next)) cir.setReturnValue(true);
    }
}
