package net.ixdarklord.coolcatcore.internal.attachment;

import org.jetbrains.annotations.Nullable;

// Mixed into Entity and BlockEntity: their data store, created on first use.
public interface AttachmentStoreAccess {
    @Nullable AttachmentStore coolcatcore$getAttachments();

    AttachmentStore coolcatcore$getOrCreateAttachments();
}
