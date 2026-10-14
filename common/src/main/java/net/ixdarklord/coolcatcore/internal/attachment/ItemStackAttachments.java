package net.ixdarklord.coolcatcore.internal.attachment;

import net.ixdarklord.coolcatcore.api.attachment.Attachment;
import net.ixdarklord.coolcatcore.api.attachment.AttachmentHolder;
import net.ixdarklord.coolcatcore.api.attachment.AttachmentRegistry;
import net.ixdarklord.coolcatcore.api.attachment.Trackable;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

// A stack's attachment values, read from and written to each mod's <modid>:attachments component on every call.
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

    private AttachmentBundle bundle(Attachment<?> attachment) {
        return this.stack.getOrDefault(attachment.registry().bundleComponent(), AttachmentBundle.EMPTY);
    }

    @Override
    public <T> T get(Attachment<T> attachment) {
        T value = this.bundle(attachment).get(attachment);
        return value != null ? value : attachment.createDefault();
    }

    @Override
    public <T> void set(Attachment<T> attachment, T value) {
        Objects.requireNonNull(value, () -> "Can't set " + attachment.id() + " to null; use reset");
        if (this.stack.isEmpty()) throw new IllegalArgumentException("Can't store " + attachment.id() + " on an empty stack");
        if (!attachment.isPersistent()) throw new IllegalArgumentException("The transient attachment " + attachment.id() + " can't be stored on an item stack");
        if (value instanceof Trackable) throw new IllegalArgumentException("Item stack data must be immutable; " + attachment.id() + " holds a Trackable value");
        T old = this.bundle(attachment).get(attachment);
        if ((old != null ? old : attachment.createDefault()).equals(value)) return;
        Object outcome = AttachmentEventHooks.changing(this, attachment, old, value);
        if (outcome == AttachmentEventHooks.CANCELLED) return;
        @SuppressWarnings("unchecked") T stored = (T) outcome;
        // A default value is stored as no value, so stacks with and without it still stack together.
        T kept = stored.equals(attachment.createDefault()) ? null : stored;
        if (Objects.equals(old, kept)) return;
        this.write(attachment, this.bundle(attachment).with(attachment, kept));
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
        return this.bundle(attachment).get(attachment) != null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void reset(Attachment<?> attachment) {
        Attachment<Object> raw = (Attachment<Object>) attachment;
        Object old = this.bundle(raw).get(raw);
        if (old == null) return;
        Object outcome = AttachmentEventHooks.changing(this, raw, old, null);
        if (outcome == AttachmentEventHooks.CANCELLED) return;
        if (outcome != null) {
            // A listener handed a value to keep instead of removing it.
            this.set(raw, outcome);
            return;
        }
        this.write(raw, this.bundle(raw).with(raw, null));
        AttachmentEventHooks.changed(this, raw, old, null);
    }

    @Override
    public Set<Attachment<?>> attachments() {
        Set<Attachment<?>> keys = new LinkedHashSet<>();
        for (AttachmentRegistry registry : AttachmentRegistry.all()) {
            if (!registry.isRegistered()) continue;
            AttachmentBundle bundle = this.stack.get(registry.bundleComponent());
            if (bundle != null) keys.addAll(bundle.values().keySet());
        }
        return Collections.unmodifiableSet(keys);
    }

    private void write(Attachment<?> attachment, @Nullable AttachmentBundle bundle) {
        DataComponentType<AttachmentBundle> type = attachment.registry().bundleComponent();
        if (bundle == null || bundle.isEmpty()) this.stack.remove(type);
        else this.stack.set(type, bundle);
    }
}
