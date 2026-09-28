package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.api.attachment.Attachment;
import net.ixdarklord.coolcatcore.internal.attachment.AttachmentStore;
import net.ixdarklord.coolcatcore.internal.attachment.AttachmentStoreAccess;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The player respawned (or left the End) as a new object: carries over the data it keeps.
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Inject(method = "restoreFrom", at = @At("TAIL"))
    private void coolcatcore$restoreData(ServerPlayer oldPlayer, boolean restoreAll, CallbackInfo ci) {
        AttachmentStore old = ((AttachmentStoreAccess) oldPlayer).coolcatcore$getAttachments();
        if (old == null) return;
        ((AttachmentStoreAccess) (Object) this).coolcatcore$getOrCreateAttachments().copyFrom(old, (Attachment<?> key) -> restoreAll || key.copyOnDeath(), oldPlayer.registryAccess());
    }
}
