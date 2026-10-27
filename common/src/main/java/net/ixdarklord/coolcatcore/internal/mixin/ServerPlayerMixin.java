package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.api.attachment.Attachment;
import net.ixdarklord.coolcatcore.api.event.v2.common.PlayerEvents;
import net.ixdarklord.coolcatcore.internal.attachment.AttachmentStore;
import net.ixdarklord.coolcatcore.internal.attachment.AttachmentStoreAccess;
import net.ixdarklord.coolcatcore.internal.event.DimensionTravelHooks;
import net.ixdarklord.coolcatcore.internal.event.MerchantOffersView;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;

// The player respawned (or left the End) as a new object: carries over the data it keeps. And PlayerEvents
// .TRAVEL_TO_DIMENSION before a teleport to another level (changeDimension is each loader's own ServerPlayerMixin's, as
// Forge changes its signature).
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    // The teleportTo that takes relative movements calls this one for another level.
    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void coolcatlib$beforeTravel(ServerLevel level, double x, double y, double z, float yRot, float xRot, CallbackInfo ci) {
        if (!DimensionTravelHooks.mayTravel((ServerPlayer) (Object) this, level)) ci.cancel();
    }

    // PlayerEvents.OPEN_MENU, for any menu the player is shown.
    @Inject(method = "openMenu(Lnet/minecraft/world/MenuProvider;)Ljava/util/OptionalInt;", at = @At("HEAD"), cancellable = true)
    private void coolcatlib$beforeOpenMenu(@Nullable MenuProvider provider, CallbackInfoReturnable<OptionalInt> cir) {
        if (provider != null && PlayerEvents.OPEN_MENU.hasListeners()
                && PlayerEvents.OPEN_MENU.invoker().onOpenMenu((ServerPlayer) (Object) this, provider).isInterrupt()) {
            cir.setReturnValue(OptionalInt.empty());
        }
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

    @Inject(method = "restoreFrom", at = @At("TAIL"))
    private void coolcatlib$restoreData(ServerPlayer oldPlayer, boolean restoreAll, CallbackInfo ci) {
        AttachmentStore old = ((AttachmentStoreAccess) oldPlayer).coolcatlib$getAttachments();
        if (old == null) return;
        ((AttachmentStoreAccess) (Object) this).coolcatlib$getOrCreateAttachments().copyFrom(old, (Attachment<?> key) -> restoreAll || key.copyOnDeath(), oldPlayer.level().registryAccess());
    }
}
