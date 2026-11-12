package net.ixdarklord.coolcatcore.internal.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ixdarklord.coolcatcore.internal.event.MerchantOffersView;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// MerchantEvents.OFFERS: the trade the items put in make is picked from the player's own list (MerchantOffersView).
// Wrapped rather than redirected, so other mods can change the same call too.
@Mixin(MerchantContainer.class)
public abstract class MerchantContainerMixin {
    @WrapOperation(method = "updateSellItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/trading/Merchant;getOffers()Lnet/minecraft/world/item/trading/MerchantOffers;"))
    private MerchantOffers coolcatlib$playerOffers(Merchant merchant, Operation<MerchantOffers> getOffers) {
        return MerchantOffersView.tradedFrom(merchant, getOffers.call(merchant));
    }
}
