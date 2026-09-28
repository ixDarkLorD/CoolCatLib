package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.api.attachment.Attachment;
import net.ixdarklord.coolcatcore.api.attachment.AttachmentRegistry;
import net.ixdarklord.coolcatcore.internal.attachment.AttachmentBundle;
import net.ixdarklord.coolcatcore.internal.attachment.AttachmentStore;
import net.ixdarklord.coolcatcore.internal.attachment.AttachmentStoreAccess;
import net.ixdarklord.coolcatcore.internal.handler.HandlerCaches;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

// Every block entity's Attachment values: saved with its data, kept in its item for keepOnDrop keys, and invalidating
// cached handlers at its position when it's removed or comes back.
@Mixin(BlockEntity.class)
public abstract class BlockEntityMixin implements AttachmentStoreAccess {
    @Shadow
    protected @Nullable Level level;
    @Shadow
    @Final
    protected BlockPos worldPosition;

    @Unique
    private @Nullable AttachmentStore coolcatcore$attachments;

    @Shadow
    protected abstract void collectImplicitComponents(DataComponentMap.Builder components);

    @Shadow
    protected abstract void applyImplicitComponents(BlockEntity.DataComponentInput components);

    @Override
    public @Nullable AttachmentStore coolcatcore$getAttachments() {
        return this.coolcatcore$attachments;
    }

    @Override
    public AttachmentStore coolcatcore$getOrCreateAttachments() {
        if (this.coolcatcore$attachments == null) this.coolcatcore$attachments = new AttachmentStore(this);
        return this.coolcatcore$attachments;
    }

    @Inject(method = {"saveWithoutMetadata", "saveCustomOnly"}, at = @At("RETURN"))
    private void coolcatcore$saveAttachments(HolderLookup.Provider registries, CallbackInfoReturnable<CompoundTag> cir) {
        if (this.coolcatcore$attachments != null) this.coolcatcore$attachments.save(cir.getReturnValue(), registries);
    }

    // Clients get values only through syncing, not from update tags that happen to contain saved data.
    @Inject(method = {"loadWithComponents", "loadCustomOnly"}, at = @At("TAIL"))
    private void coolcatcore$loadAttachments(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        if (this.level != null && this.level.isClientSide()) return;
        if (AttachmentStore.hasSaved(tag)) this.coolcatcore$getOrCreateAttachments().load(tag, registries);
    }

    @Inject(method = {"setRemoved", "clearRemoved"}, at = @At("TAIL"))
    private void coolcatcore$invalidateHandlers(CallbackInfo ci) {
        if (this.level != null) HandlerCaches.invalidate(this.level, this.worldPosition);
    }

    @Redirect(method = "collectComponents", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/BlockEntity;collectImplicitComponents(Lnet/minecraft/core/component/DataComponentMap$Builder;)V"))
    private void coolcatcore$collectData(BlockEntity self, DataComponentMap.Builder components) {
        this.collectImplicitComponents(components);
        if (this.coolcatcore$attachments == null) return;
        // Each mod's kept values go in that mod's own component.
        Map<AttachmentRegistry, AttachmentBundle> bundles = new HashMap<>();
        for (Attachment<?> key : this.coolcatcore$attachments.attachments()) {
            if (!key.keepOnDrop()) continue;
            bundles.put(key.registry(), bundles.getOrDefault(key.registry(), AttachmentBundle.EMPTY).with(key, this.coolcatcore$attachments.get(key)));
        }
        bundles.forEach((registry, bundle) -> components.set(registry.bundleComponent(), bundle));
    }

    @Redirect(method = "applyComponents", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/BlockEntity;applyImplicitComponents(Lnet/minecraft/world/level/block/entity/BlockEntity$DataComponentInput;)V"))
    @SuppressWarnings("unchecked")
    private void coolcatcore$applyData(BlockEntity self, BlockEntity.DataComponentInput components) {
        this.applyImplicitComponents(components);
        // Reading a component also takes it off the block entity's own components.
        for (AttachmentRegistry registry : AttachmentRegistry.all()) {
            if (!registry.isRegistered()) continue;
            AttachmentBundle bundle = components.get(registry.bundleComponent());
            if (bundle == null) continue;
            AttachmentStore store = this.coolcatcore$getOrCreateAttachments();
            for (Map.Entry<Attachment<?>, Object> entry : bundle.values().entrySet()) {
                if (entry.getKey().keepOnDrop()) store.set((Attachment<Object>) entry.getKey(), entry.getValue());
            }
        }
    }
}
