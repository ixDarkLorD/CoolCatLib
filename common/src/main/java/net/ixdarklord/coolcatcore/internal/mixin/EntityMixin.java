package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.attachment.AttachmentStore;
import net.ixdarklord.coolcatcore.internal.attachment.AttachmentStoreAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
            target = "Lnet/minecraft/world/entity/Entity;addAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueOutput;)V"))
    private void coolcatlib$saveAttachments(ValueOutput output, CallbackInfo ci) {
        if (this.coolcatlib$attachments != null) this.coolcatlib$attachments.save(output);
    }

    @Inject(method = "load", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/world/entity/Entity;readAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueInput;)V"))
    private void coolcatlib$loadAttachments(ValueInput input, CallbackInfo ci) {
        if (AttachmentStore.hasSaved(input)) this.coolcatlib$getOrCreateAttachments().load(input);
    }
}
