package net.ixdarklord.coolcatcore.internal.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import net.ixdarklord.coolcatcore.api.attachment.Attachment;
import net.ixdarklord.coolcatcore.api.attachment.AttachmentHolder;
import net.ixdarklord.coolcatcore.api.attachment.AttachmentRegistry;
import net.ixdarklord.coolcatcore.api.attachment.Trackable;
import net.ixdarklord.coolcatcore.api.utils.CodecUtils;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

// A stack's attachment values, read from and written to each mod's <modid>:attachments tag of the stack's NBT on every
// call. Values are stored by attachment name; a default value is stored as no value, and an emptied tag is removed, so
// stacks with and without it still stack together.
public record ItemStackAttachments(ItemStack stack) implements AttachmentHolder {
    @Override
    public Object owner() {
        return this.stack;
    }

    @Override
    public boolean isClientSide() {
        return false;
    }

    @Override
    public boolean isValid() {
        return !this.stack.isEmpty();
    }

    private static DynamicOps<Tag> ops() {
        return CodecUtils.registryOps(NbtOps.INSTANCE);
    }

    private @Nullable CompoundTag data(AttachmentRegistry registry) {
        CompoundTag tag = this.stack.getTag();
        return tag != null && tag.contains(registry.tagName(), Tag.TAG_COMPOUND) ? tag.getCompound(registry.tagName()) : null;
    }

    // The stored value, or null for none (or one that can't be read).
    private <T> @Nullable T read(Attachment<T> attachment) {
        Codec<T> codec = attachment.codec();
        if (codec == null) return null;
        CompoundTag data = this.data(attachment.registry());
        if (data == null || !data.contains(attachment.name())) return null;
        return codec.parse(ops(), data.get(attachment.name()))
                .resultOrPartial(error -> CoolCatCore.LOGGER.error("Couldn't read item attachment {}: {}", attachment.id(), error))
                .orElse(null);
    }

    @Override
    public <T> T get(Attachment<T> attachment) {
        T value = this.read(attachment);
        return value != null ? value : attachment.createDefault();
    }

    @Override
    public <T> void set(Attachment<T> attachment, T value) {
        Objects.requireNonNull(value, () -> "Can't set " + attachment.id() + " to null; use reset");
        if (this.stack.isEmpty()) throw new IllegalArgumentException("Can't store " + attachment.id() + " on an empty stack");
        if (!attachment.isPersistent()) throw new IllegalArgumentException("The transient attachment " + attachment.id() + " can't be stored on an item stack");
        if (value instanceof Trackable) throw new IllegalArgumentException("Item stack data must be immutable; " + attachment.id() + " holds a Trackable value");
        T old = this.read(attachment);
        if ((old != null ? old : attachment.createDefault()).equals(value)) return;
        Object outcome = AttachmentEventHooks.changing(this, attachment, old, value);
        if (outcome == AttachmentEventHooks.CANCELLED) return;
        @SuppressWarnings("unchecked") T stored = (T) outcome;
        // A default value is stored as no value, so stacks with and without it still stack together.
        T kept = stored.equals(attachment.createDefault()) ? null : stored;
        if (Objects.equals(old, kept)) return;
        this.write(attachment, kept);
        AttachmentEventHooks.changed(this, attachment, old, kept);
    }

    @Override
    public <T> void modify(Attachment<T> attachment, Consumer<T> mutator) {
        throw new UnsupportedOperationException("Item stack data is immutable; use update to set " + attachment.id() + " to a changed copy");
    }

    @Override
    public void markDirty(Attachment<?> attachment) {
        // Every change is written straight to the stack.
    }

    @Override
    public boolean has(Attachment<?> attachment) {
        return this.read(attachment) != null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void reset(Attachment<?> attachment) {
        Attachment<Object> raw = (Attachment<Object>) attachment;
        Object old = this.read(raw);
        if (old == null) return;
        Object outcome = AttachmentEventHooks.changing(this, raw, old, null);
        if (outcome == AttachmentEventHooks.CANCELLED) return;
        if (outcome != null) {
            // A listener handed a value to keep instead of removing it.
            this.set(raw, outcome);
            return;
        }
        this.write(raw, null);
        AttachmentEventHooks.changed(this, raw, old, null);
    }

    @Override
    public Set<Attachment<?>> attachments() {
        Set<Attachment<?>> keys = new LinkedHashSet<>();
        for (AttachmentRegistry registry : AttachmentRegistry.all()) {
            CompoundTag data = this.data(registry);
            if (data == null) continue;
            for (String name : data.getAllKeys()) {
                Attachment<?> attachment = registry.get(name);
                if (attachment != null && attachment.isPersistent()) keys.add(attachment);
            }
        }
        return Collections.unmodifiableSet(keys);
    }

    // Stores the value, or removes it for null; an emptied mod tag (and stack tag) is removed.
    private <T> void write(Attachment<T> attachment, @Nullable T value) {
        String tagName = attachment.registry().tagName();
        if (value == null) {
            CompoundTag data = this.data(attachment.registry());
            if (data == null) return;
            data.remove(attachment.name());
            if (data.isEmpty()) this.stack.removeTagKey(tagName);
            return;
        }
        Tag encoded = Objects.requireNonNull(attachment.codec()).encodeStart(ops(), value)
                .getOrThrow(false, error -> CoolCatCore.LOGGER.error("Couldn't write item attachment {}: {}", attachment.id(), error));
        this.stack.getOrCreateTagElement(tagName).put(attachment.name(), encoded);
    }
}
