package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.api.attachment.Attachment;
import net.ixdarklord.coolcatcore.api.attachment.AttachmentRegistry;
import net.ixdarklord.coolcatcore.internal.attachment.AttachmentBundle;
import net.ixdarklord.coolcatcore.internal.attachment.AttachmentStore;
import net.ixdarklord.coolcatcore.internal.attachment.AttachmentStoreAccess;
import net.ixdarklord.coolcatcore.internal.handler.HandlerCaches;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
    private @Nullable AttachmentStore coolcatlib$attachments;

    @Shadow
    private DataComponentMap components;

    @Override
    public @Nullable AttachmentStore coolcatlib$getAttachments() {
        return this.coolcatlib$attachments;
    }

    @Override
    public AttachmentStore coolcatlib$getOrCreateAttachments() {
        if (this.coolcatlib$attachments == null) this.coolcatlib$attachments = new AttachmentStore(this);
        return this.coolcatlib$attachments;
    }

    @Inject(method = {"saveWithoutMetadata(Lnet/minecraft/world/level/storage/ValueOutput;)V", "saveCustomOnly(Lnet/minecraft/world/level/storage/ValueOutput;)V"}, at = @At("TAIL"))
    private void coolcatlib$saveAttachments(ValueOutput output, CallbackInfo ci) {
        if (this.coolcatlib$attachments != null) this.coolcatlib$attachments.save(output);
    }

    // Clients get values only through syncing, not from update tags that happen to contain saved data.
    @Inject(method = {"loadWithComponents", "loadCustomOnly"}, at = @At("TAIL"))
    private void coolcatlib$loadAttachments(ValueInput input, CallbackInfo ci) {
        if (this.level != null && this.level.isClientSide()) return;
        if (AttachmentStore.hasSaved(input)) this.coolcatlib$getOrCreateAttachments().load(input);
    }

    @Inject(method = {"setRemoved", "clearRemoved"}, at = @At("TAIL"))
    private void coolcatlib$invalidateHandlers(CallbackInfo ci) {
        if (this.level != null) HandlerCaches.invalidate(this.level, this.worldPosition);
    }

    // Plain injects rather than redirects: the MixinExtras NeoForge ships misreads a compiled @Redirect's target.
    @Inject(method = "collectComponents", at = @At("RETURN"), cancellable = true)
    private void coolcatlib$collectData(CallbackInfoReturnable<DataComponentMap> cir) {
        if (this.coolcatlib$attachments == null) return;
        // Each mod's kept values go in that mod's own component.
        Map<AttachmentRegistry, AttachmentBundle> bundles = new HashMap<>();
        for (Attachment<?> key : this.coolcatlib$attachments.attachments()) {
            if (!key.keepOnDrop()) continue;
            bundles.put(key.registry(), bundles.getOrDefault(key.registry(), AttachmentBundle.EMPTY).with(key, this.coolcatlib$attachments.get(key)));
        }
        if (bundles.isEmpty()) return;
        DataComponentMap.Builder components = DataComponentMap.builder().addAll(cir.getReturnValue());
        bundles.forEach((registry, bundle) -> components.set(registry.bundleComponent(), bundle));
        cir.setReturnValue(components.build());
    }

    @Inject(method = "applyComponents", at = @At("TAIL"))
    @SuppressWarnings("unchecked")
    private void coolcatlib$applyData(DataComponentMap prototype, DataComponentPatch patch, CallbackInfo ci) {
        DataComponentMap components = PatchedDataComponentMap.fromPatch(prototype, patch);
        Set<DataComponentType<?>> read = new HashSet<>();
        for (AttachmentRegistry registry : AttachmentRegistry.all()) {
            if (!registry.isRegistered()) continue;
            AttachmentBundle bundle = components.get(registry.bundleComponent());
            if (bundle == null) continue;
            read.add(registry.bundleComponent());
            AttachmentStore store = this.coolcatlib$getOrCreateAttachments();
            for (Map.Entry<Attachment<?>, Object> entry : bundle.values().entrySet()) {
                if (entry.getKey().keepOnDrop()) store.set((Attachment<Object>) entry.getKey(), entry.getValue());
            }
        }
        // What was read doesn't stay among the block entity's own components, as with vanilla's implicit ones.
        if (!read.isEmpty()) this.components = this.components.filter(type -> !read.contains(type));
    }
}
