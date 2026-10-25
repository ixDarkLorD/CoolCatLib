package net.ixdarklord.coolcatcore.internal.attachment;

import org.jetbrains.annotations.Nullable;

// Mixed into Entity, BlockEntity, Level and MinecraftServer: their data store, created on first use.
public interface AttachmentStoreAccess {
    @Nullable AttachmentStore coolcatlib$getAttachments();

    AttachmentStore coolcatlib$getOrCreateAttachments();
}
