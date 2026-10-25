package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.attachment.AttachmentStore;
import net.ixdarklord.coolcatcore.internal.attachment.AttachmentStoreAccess;
import net.ixdarklord.coolcatcore.internal.attachment.ServerAttachments;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

// The server's own Attachment values, saved in the overworld's data folder; and its player data, for offline players'.
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin implements AttachmentStoreAccess, ServerAttachments.PlayerDataAccess {
    @Shadow
    @Final
    protected PlayerDataStorage playerDataStorage;
    @Unique
    private @Nullable AttachmentStore coolcatlib$attachments;

    @Override
    public @Nullable AttachmentStore coolcatlib$getAttachments() {
        this.coolcatlib$attachments = ServerAttachments.existingServer((MinecraftServer) (Object) this, this.coolcatlib$attachments);
        return this.coolcatlib$attachments;
    }

    @Override
    public AttachmentStore coolcatlib$getOrCreateAttachments() {
        this.coolcatlib$attachments = ServerAttachments.server((MinecraftServer) (Object) this, this.coolcatlib$attachments);
        return this.coolcatlib$attachments;
    }

    @Override
    public PlayerDataStorage coolcatlib$playerData() {
        return this.playerDataStorage;
    }
}
