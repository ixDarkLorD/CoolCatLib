package net.ixdarklord.coolcatcore.api.attachment;

import net.ixdarklord.coolcatcore.internal.attachment.AttachmentStoreAccess;
import net.ixdarklord.coolcatcore.internal.attachment.ItemStackAttachments;
import net.ixdarklord.coolcatcore.internal.attachment.ServerAttachments;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * The {@link Attachment} values of one entity, block entity, item stack, level or the server. {@link Attachment}'s own methods
 * ({@code MADNESS.get(player)}) are shortcuts for these.
 * <p>
 * Entities and block entities keep their values in memory, save them with their other data and sync them by each
 * attachment's {@link SyncPolicy}. Values set on a client stay on that client until the server's next sync. Item stacks keep
 * theirs in their mod's {@code <modid>:attachments} component, which travels with the stack like any other component.
 * Levels and the server keep theirs in saved data, in the level's and the server's data folder.
 */
public interface AttachmentHolder {
    static AttachmentHolder of(Entity entity) {
        return ((AttachmentStoreAccess) entity).coolcatlib$getOrCreateAttachments();
    }

    static AttachmentHolder of(BlockEntity blockEntity) {
        return ((AttachmentStoreAccess) blockEntity).coolcatlib$getOrCreateAttachments();
    }

    /** A level's values: a server level's saved in its data folder, a client level's as the server sent them. */
    static AttachmentHolder of(Level level) {
        return ((AttachmentStoreAccess) level).coolcatlib$getOrCreateAttachments();
    }

    /** The server's own values, saved in its data folder. */
    static AttachmentHolder of(MinecraftServer server) {
        return ((AttachmentStoreAccess) server).coolcatlib$getOrCreateAttachments();
    }

    /** The server's values as a side knows them: the server's own, or on a client the copy it was sent. */
    static AttachmentHolder global(Level level) {
        return ServerAttachments.global(level);
    }

    /** A view of the stack's data; it reads and writes the stack directly, so it's cheap and never out of date. */
    static AttachmentHolder of(ItemStack stack) {
        return new ItemStackAttachments(stack);
    }

    /** The entity, block entity, item stack, level or server holding the values. */
    Object owner();

    /** Whether the holder is on a client. Always {@code false} for item stacks, which have no side. */
    boolean isClientSide();

    /** Whether the holder still exists: the entity or block entity isn't removed, the stack isn't empty. */
    boolean isValid();

    /** The value, or the attachment's default if none was set. */
    <T> T get(Attachment<T> attachment);

    /** Sets the value. Setting an equal value does nothing. */
    <T> void set(Attachment<T> attachment, T value);

    /** Sets the value to {@code function}'s result and returns it. */
    default <T> T update(Attachment<T> attachment, UnaryOperator<T> function) {
        T value = function.apply(this.get(attachment));
        this.set(attachment, value);
        return value;
    }

    /**
     * Changes a mutable value in place, then saves and syncs it. Not for item stacks, whose values are immutable: use
     * {@link #update} there.
     */
    <T> void modify(Attachment<T> attachment, Consumer<T> mutator);

    /**
     * Saves and syncs a value that was changed in place, without {@link #modify}.
     */
    void markDirty(Attachment<?> attachment);

    /** Whether a value is stored, rather than read from the default. */
    boolean has(Attachment<?> attachment);

    /** Removes the value, so the default is read again. */
    void reset(Attachment<?> attachment);

    /** The attachments with a stored value. */
    Set<Attachment<?>> attachments();
}
