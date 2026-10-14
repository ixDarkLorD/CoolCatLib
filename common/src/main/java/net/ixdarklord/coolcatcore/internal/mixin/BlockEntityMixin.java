package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.attachment.AttachmentStore;
import net.ixdarklord.coolcatcore.internal.attachment.AttachmentStoreAccess;
import net.ixdarklord.coolcatcore.internal.handler.HandlerCaches;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Every block entity's Attachment values: saved with its data (which also carries keepOnDrop values into its dropped
// item, through the loot table's copy_nbt, and back when that item is placed), and invalidating cached handlers at its
// position when it's removed or comes back.
@Mixin(BlockEntity.class)
public abstract class BlockEntityMixin implements AttachmentStoreAccess {
    @Shadow
    protected @Nullable Level level;
    @Shadow
    @Final
    protected BlockPos worldPosition;

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

    // saveWithoutMetadata is final and behind every save (chunks, saveWithId, saveWithFullMetadata, saveToItem), so
    // subclasses overriding saveAdditional without calling super don't lose the values.
    @Inject(method = "saveWithoutMetadata", at = @At("RETURN"))
    private void coolcatlib$saveAttachments(CallbackInfoReturnable<CompoundTag> cir) {
        if (this.coolcatlib$attachments != null) this.coolcatlib$attachments.save(cir.getReturnValue());
    }

    // Clients get values only through syncing, not from update tags that happen to contain saved data.
    @Inject(method = "load", at = @At("TAIL"))
    private void coolcatlib$loadAttachments(CompoundTag input, CallbackInfo ci) {
        if (this.level != null && this.level.isClientSide()) return;
        if (AttachmentStore.hasSaved(input)) this.coolcatlib$getOrCreateAttachments().load(input);
    }

    @Inject(method = {"setRemoved", "clearRemoved"}, at = @At("TAIL"))
    private void coolcatlib$invalidateHandlers(CallbackInfo ci) {
        if (this.level != null) HandlerCaches.invalidate(this.level, this.worldPosition);
    }
}
