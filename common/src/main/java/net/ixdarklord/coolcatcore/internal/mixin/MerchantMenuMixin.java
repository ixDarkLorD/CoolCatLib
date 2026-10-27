package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.event.MerchantOffersView;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// MerchantEvents.OFFERS: a trading menu trades from the list its player was sent (MerchantOffersView).
@Mixin(MerchantMenu.class)
public abstract class MerchantMenuMixin implements MerchantOffersView.Menu {
    @Shadow
    @Final
    private Merchant trader;
    @Unique
    private @Nullable MerchantOffers coolcatlib$offers;

    @Override
    public Merchant coolcatlib$getTrader() {
        return this.trader;
    }

    @Override
    public @Nullable MerchantOffers coolcatlib$getOffers() {
        return this.coolcatlib$offers;
    }

    @Override
    public void coolcatlib$setOffers(MerchantOffers offers) {
        this.coolcatlib$offers = offers;
    }

    @Inject(method = "getOffers", at = @At("RETURN"), cancellable = true)
    private void coolcatlib$playerOffers(CallbackInfoReturnable<MerchantOffers> cir) {
        if (this.coolcatlib$offers != null) cir.setReturnValue(this.coolcatlib$offers);
    }
}
