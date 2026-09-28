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
    private @Nullable AttachmentStore coolcatcore$attachments;

    @Override
    public @Nullable AttachmentStore coolcatcore$getAttachments() {
        return this.coolcatcore$attachments;
    }

    @Override
    public AttachmentStore coolcatcore$getOrCreateAttachments() {
        if (this.coolcatcore$attachments == null) this.coolcatcore$attachments = new AttachmentStore(this);
        return this.coolcatcore$attachments;
    }

    @Inject(method = "saveWithoutId", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/world/entity/Entity;addAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueOutput;)V"))
    private void coolcatcore$saveAttachments(ValueOutput output, CallbackInfo ci) {
        if (this.coolcatcore$attachments != null) this.coolcatcore$attachments.save(output);
    }

    @Inject(method = "load", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/world/entity/Entity;readAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueInput;)V"))
    private void coolcatcore$loadAttachments(ValueInput input, CallbackInfo ci) {
        if (AttachmentStore.hasSaved(input)) this.coolcatcore$getOrCreateAttachments().load(input);
    }
}
