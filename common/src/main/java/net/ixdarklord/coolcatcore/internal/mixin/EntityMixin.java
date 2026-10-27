package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.api.event.v2.common.EntityEvents;
import net.ixdarklord.coolcatcore.internal.attachment.AttachmentStore;
import net.ixdarklord.coolcatcore.internal.attachment.AttachmentStoreAccess;
import net.ixdarklord.coolcatcore.internal.event.SpawnTracking;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Every entity's Attachment values, saved and loaded with its own data (which also carries them across dimensions).
// What EntityEvents.SPAWN knows of a new entity (SpawnTracking), and EntityEvents.MOUNT.
@Mixin(Entity.class)
public abstract class EntityMixin implements AttachmentStoreAccess, SpawnTracking.Tracked {
    @Unique
    private @Nullable AttachmentStore coolcatlib$attachments;
    @Unique
    private @Nullable EntitySpawnReason coolcatlib$spawnReason;
    @Unique
    private boolean coolcatlib$spawnRefused;
    @Unique
    private boolean coolcatlib$changingVehicle;

    @Override
    public @Nullable AttachmentStore coolcatlib$getAttachments() {
        return this.coolcatlib$attachments;
    }

    @Override
    public AttachmentStore coolcatlib$getOrCreateAttachments() {
        if (this.coolcatlib$attachments == null) this.coolcatlib$attachments = new AttachmentStore(this);
        return this.coolcatlib$attachments;
    }

    @Inject(method = "saveWithoutId", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/world/entity/Entity;addAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueOutput;)V"))
    private void coolcatlib$saveAttachments(ValueOutput output, CallbackInfo ci) {
        if (this.coolcatlib$attachments != null) this.coolcatlib$attachments.save(output);
    }

    @Inject(method = "load", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/world/entity/Entity;readAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueInput;)V"))
    private void coolcatlib$loadAttachments(ValueInput input, CallbackInfo ci) {
        if (AttachmentStore.hasSaved(input)) this.coolcatlib$getOrCreateAttachments().load(input);
    }

    @Override
    public void coolcatlib$setSpawnReason(EntitySpawnReason reason) {
        this.coolcatlib$spawnReason = reason;
    }

    @Override
    public @Nullable EntitySpawnReason coolcatlib$takeSpawnReason() {
        EntitySpawnReason reason = this.coolcatlib$spawnReason;
        this.coolcatlib$spawnReason = null;
        return reason;
    }

    @Override
    public void coolcatlib$refuseSpawn() {
        this.coolcatlib$spawnRefused = true;
    }

    @Override
    public boolean coolcatlib$isSpawnRefused() {
        return this.coolcatlib$spawnRefused;
    }

    // Once riding is allowed (it isn't riding this already, and may ride it), before it gets off what it rides now.
    @Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z", cancellable = true,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;isPassenger()Z"))
    private void coolcatlib$beforeMount(Entity vehicle, boolean force, boolean sendEventAndTriggers, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide() || !EntityEvents.MOUNT.hasListeners()) return;
        if (EntityEvents.MOUNT.invoker().onMount(self, vehicle, true).isInterrupt()) cir.setReturnValue(false);
        else this.coolcatlib$changingVehicle = true;
    }

    @Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setPose(Lnet/minecraft/world/entity/Pose;)V"))
    private void coolcatlib$mounting(Entity vehicle, boolean force, boolean sendEventAndTriggers, CallbackInfoReturnable<Boolean> cir) {
        this.coolcatlib$changingVehicle = false;
    }

    // (Getting off one vehicle to ride another is only the mount.)
    @Inject(method = "removeVehicle", at = @At("HEAD"))
    private void coolcatlib$dismount(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        Entity vehicle = self.getVehicle();
        if (vehicle == null || this.coolcatlib$changingVehicle || self.level().isClientSide() || !EntityEvents.MOUNT.hasListeners()) return;
        EntityEvents.MOUNT.invoker().onMount(self, vehicle, false);
    }
}
