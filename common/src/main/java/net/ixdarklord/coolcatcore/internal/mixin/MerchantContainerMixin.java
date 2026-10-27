package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.event.MerchantOffersView;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// MerchantEvents.OFFERS: the trade the items put in make is picked from the player's own list (MerchantOffersView).
@Mixin(MerchantContainer.class)
public abstract class MerchantContainerMixin {
    @Redirect(method = "updateSellItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/trading/Merchant;getOffers()Lnet/minecraft/world/item/trading/MerchantOffers;"))
    private MerchantOffers coolcatlib$playerOffers(Merchant merchant) {
        return MerchantOffersView.tradedFrom(merchant, merchant.getOffers());
    }
}
