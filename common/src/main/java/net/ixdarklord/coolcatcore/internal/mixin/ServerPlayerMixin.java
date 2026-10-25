package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.api.attachment.Attachment;
import net.ixdarklord.coolcatcore.internal.attachment.AttachmentStore;
import net.ixdarklord.coolcatcore.internal.attachment.AttachmentStoreAccess;
import net.ixdarklord.coolcatcore.internal.event.DimensionTravelHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    @Inject(method = "restoreFrom", at = @At("TAIL"))
    private void coolcatlib$restoreData(ServerPlayer oldPlayer, boolean restoreAll, CallbackInfo ci) {
        AttachmentStore old = ((AttachmentStoreAccess) oldPlayer).coolcatlib$getAttachments();
        if (old == null) return;
        ((AttachmentStoreAccess) (Object) this).coolcatlib$getOrCreateAttachments().copyFrom(old, (Attachment<?> key) -> restoreAll || key.copyOnDeath(), oldPlayer.level().registryAccess());
    }
}
