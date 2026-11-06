package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.api.attachment.Attachment;
import net.ixdarklord.coolcatcore.api.event.v2.common.PlayerEvents;
import net.ixdarklord.coolcatcore.internal.attachment.AttachmentStore;
import net.ixdarklord.coolcatcore.internal.attachment.AttachmentStoreAccess;
import net.ixdarklord.coolcatcore.internal.event.MenuOpening;
import net.ixdarklord.coolcatcore.internal.event.MerchantOffersView;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.portal.DimensionTransition;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;

// The player respawned (or left the End) as a new object: carries over the data it keeps. And PlayerEvents
// .TRAVEL_TO_DIMENSION, before any move to another dimension (portals, commands and teleportTo with another level
// all go through changeDimension on this version).
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin implements MenuOpening {
    @Unique
    private boolean coolcatlib$openingMenu;

    @Inject(method = "changeDimension", at = @At("HEAD"), cancellable = true)
    private void coolcatlib$beforeTravel(DimensionTransition transition, CallbackInfoReturnable<Entity> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (self.isRemoved() || transition.newLevel() == self.level() || !PlayerEvents.TRAVEL_TO_DIMENSION.hasListeners()) return;
        if (PlayerEvents.TRAVEL_TO_DIMENSION.invoker().onTravelToDimension(self, self.level().dimension(), transition.newLevel().dimension()).isInterrupt()) {
            // Standing in a portal, they'd be asked again every tick.
            self.setPortalCooldown();
            cir.setReturnValue(null);
        }
    }

    // PlayerEvents.OPEN_MENU, for any menu the player is shown vanilla's way. NeoForge's way with extra data, which
    // vanilla's goes through there, is its own ServerPlayerMenuMixin's (MenuOpening).
    @Inject(method = "openMenu(Lnet/minecraft/world/MenuProvider;)Ljava/util/OptionalInt;", at = @At("HEAD"), cancellable = true)
    private void coolcatlib$beforeOpenMenu(@Nullable MenuProvider provider, CallbackInfoReturnable<OptionalInt> cir) {
        if (this.coolcatlib$refusesMenu(provider)) cir.setReturnValue(OptionalInt.empty());
        else this.coolcatlib$openingMenu = true;
    }

    @Inject(method = "openMenu(Lnet/minecraft/world/MenuProvider;)Ljava/util/OptionalInt;", at = @At("RETURN"))
    private void coolcatlib$openedMenu(@Nullable MenuProvider provider, CallbackInfoReturnable<OptionalInt> cir) {
        this.coolcatlib$openingMenu = false;
    }

    // MerchantEvents.OFFERS: the player is sent their own list of a merchant's trades (MerchantOffersView).
    @Inject(method = "sendMerchantOffers", at = @At("HEAD"), cancellable = true)
    private void coolcatlib$playerOffers(int containerId, MerchantOffers offers, int merchantLevel, int merchantXp, boolean showProgressBar, boolean canRestock, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        MerchantOffers own = MerchantOffersView.forPlayer(self, containerId, offers);
        if (own == offers) return;
        self.connection.send(new ClientboundMerchantOffersPacket(containerId, own, merchantLevel, merchantXp, showProgressBar, canRestock));
        ci.cancel();
    }

    @Override
    public boolean coolcatlib$isOpeningMenu() {
        return this.coolcatlib$openingMenu;
    }

    @Override
    public boolean coolcatlib$refusesMenu(@Nullable MenuProvider provider) {
        return provider != null && PlayerEvents.OPEN_MENU.hasListeners()
                && PlayerEvents.OPEN_MENU.invoker().onOpenMenu((ServerPlayer) (Object) this, provider).isInterrupt();
    }

    @Inject(method = "restoreFrom", at = @At("TAIL"))
    private void coolcatlib$restoreData(ServerPlayer oldPlayer, boolean restoreAll, CallbackInfo ci) {
        AttachmentStore old = ((AttachmentStoreAccess) oldPlayer).coolcatlib$getAttachments();
        if (old == null) return;
        ((AttachmentStoreAccess) (Object) this).coolcatlib$getOrCreateAttachments().copyFrom(old, (Attachment<?> key) -> restoreAll || key.copyOnDeath(), oldPlayer.registryAccess());
    }
}
