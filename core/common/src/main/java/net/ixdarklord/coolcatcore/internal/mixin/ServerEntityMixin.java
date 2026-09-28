package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.attachment.AttachmentSync;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// A player started tracking an entity (its synced data follows the spawn packets), or stopped.
@Mixin(ServerEntity.class)
public abstract class ServerEntityMixin {
    @Shadow
    @Final
    private Entity entity;

    @Inject(method = "addPairing", at = @At("TAIL"))
    private void coolcatcore$syncData(ServerPlayer player, CallbackInfo ci) {
        AttachmentSync.onStartTracking(this.entity, player);
    }

    @Inject(method = "removePairing", at = @At("HEAD"))
    private void coolcatcore$stopTracking(ServerPlayer player, CallbackInfo ci) {
        AttachmentSync.onStopTracking(this.entity, player);
    }
}
