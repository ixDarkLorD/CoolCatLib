package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.attachment.AttachmentStore;
import net.ixdarklord.coolcatcore.internal.attachment.AttachmentStoreAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Every entity's Attachment values, saved and loaded with its own data (which also carries them across dimensions).
@Mixin(Entity.class)
public abstract class EntityMixin implements AttachmentStoreAccess {
    @Unique
    private @Nullable AttachmentStore coolcatlib$attachments;

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
            target = "Lnet/minecraft/world/entity/Entity;addAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V"))
    private void coolcatlib$saveAttachments(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        if (this.coolcatlib$attachments != null) this.coolcatlib$attachments.save(tag, ((Entity) (Object) this).registryAccess());
    }

    @Inject(method = "load", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/world/entity/Entity;readAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V"))
    private void coolcatlib$loadAttachments(CompoundTag tag, CallbackInfo ci) {
        if (AttachmentStore.hasSaved(tag)) this.coolcatlib$getOrCreateAttachments().load(tag, ((Entity) (Object) this).registryAccess());
    }
}
