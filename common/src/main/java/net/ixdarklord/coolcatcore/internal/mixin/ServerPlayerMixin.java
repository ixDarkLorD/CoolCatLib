package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.api.attachment.Attachment;
import net.ixdarklord.coolcatcore.internal.attachment.AttachmentStore;
import net.ixdarklord.coolcatcore.internal.attachment.AttachmentStoreAccess;
import net.ixdarklord.coolcatcore.api.event.v2.common.PlayerEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// The player respawned (or left the End) as a new object: carries over the data it keeps. And PlayerEvents
// .TRAVEL_TO_DIMENSION, before any teleport that takes the player to another dimension.
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;", at = @At("HEAD"), cancellable = true)
    private void coolcatlib$beforeTravel(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (self.isRemoved() || transition.newLevel() == self.level() || !PlayerEvents.TRAVEL_TO_DIMENSION.hasListeners()) return;
        if (PlayerEvents.TRAVEL_TO_DIMENSION.invoker().onTravelToDimension(self, self.level().dimension(), transition.newLevel().dimension()).isInterrupt()) {
            // Standing in a portal, they'd be asked again every tick.
            self.setPortalCooldown();
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "restoreFrom", at = @At("TAIL"))
    private void coolcatlib$restoreData(ServerPlayer oldPlayer, boolean restoreAll, CallbackInfo ci) {
        AttachmentStore old = ((AttachmentStoreAccess) oldPlayer).coolcatlib$getAttachments();
        if (old == null) return;
        ((AttachmentStoreAccess) (Object) this).coolcatlib$getOrCreateAttachments().copyFrom(old, (Attachment<?> key) -> restoreAll || key.copyOnDeath(), oldPlayer.registryAccess());
    }
}
