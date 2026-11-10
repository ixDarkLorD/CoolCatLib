package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.event.MerchantOffersView;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// MerchantEvents.OFFERS: the trade the items put in make is picked from the player's own list (MerchantOffersView).
@Mixin(MerchantContainer.class)
public abstract class MerchantContainerMixin {
    @Shadow
    @Final
    private Merchant merchant;

    @ModifyVariable(method = "updateSellItem", at = @At("STORE"), ordinal = 0)
    private MerchantOffers coolcatlib$playerOffers(MerchantOffers offers) {
        return MerchantOffersView.tradedFrom(this.merchant, offers);
    }
}
