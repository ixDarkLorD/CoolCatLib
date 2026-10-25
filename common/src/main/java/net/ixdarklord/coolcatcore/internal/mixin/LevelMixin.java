package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.attachment.AttachmentStore;
import net.ixdarklord.coolcatcore.internal.attachment.AttachmentStoreAccess;
import net.ixdarklord.coolcatcore.internal.attachment.ServerAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

// Every level's Attachment values: a server level's saved in its data folder, a client level's as the server sent them.
@Mixin(Level.class)
public abstract class LevelMixin implements AttachmentStoreAccess {
    @Unique
    private @Nullable AttachmentStore coolcatlib$attachments;

    @Override
    public @Nullable AttachmentStore coolcatlib$getAttachments() {
        if ((Object) this instanceof ServerLevel level) this.coolcatlib$attachments = ServerAttachments.existingLevel(level, this.coolcatlib$attachments);
        return this.coolcatlib$attachments;
    }

    @Override
    public AttachmentStore coolcatlib$getOrCreateAttachments() {
        if ((Object) this instanceof ServerLevel level) this.coolcatlib$attachments = ServerAttachments.level(level, this.coolcatlib$attachments);
        else if (this.coolcatlib$attachments == null) this.coolcatlib$attachments = new AttachmentStore(this);
        return this.coolcatlib$attachments;
    }
}
