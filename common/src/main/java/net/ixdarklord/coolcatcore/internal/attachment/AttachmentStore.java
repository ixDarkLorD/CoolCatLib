package net.ixdarklord.coolcatcore.internal.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import net.ixdarklord.coolcatcore.api.attachment.AttachmentHolder;
import net.ixdarklord.coolcatcore.api.attachment.AttachmentRegistry;
import net.ixdarklord.coolcatcore.api.attachment.Attachment;
import net.ixdarklord.coolcatcore.api.attachment.Trackable;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

// The attachment values of one entity or block entity. Saved with the owner's other data, each mod's under
// <modid>:attachments; changed synced
// keys are queued in AttachmentSync and sent at the end of the server tick.
public final class AttachmentStore implements AttachmentHolder {

    private final Object owner;
    private final Map<Attachment<?>, Object> values = new LinkedHashMap<>();
    private final Set<Attachment<?>> dirty = new LinkedHashSet<>();

    public AttachmentStore(Object owner) {
        if (!(owner instanceof Entity) && !(owner instanceof BlockEntity)) {
            throw new IllegalArgumentException("Data stores belong to entities or block entities, not " + owner);
        }
        this.owner = owner;
    }

    @Override
    public Object owner() {
        return this.owner;
    }

    public @Nullable Level level() {
        return this.owner instanceof Entity entity ? entity.level() : ((BlockEntity) this.owner).getLevel();
    }

    @Override
    public boolean isClientSide() {
        Level level = this.level();
        return level != null && level.isClientSide();
    }

    @Override
    public boolean isValid() {
        return this.owner instanceof Entity entity ? !entity.isRemoved() : !((BlockEntity) this.owner).isRemoved();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Attachment<T> key) {
        Object value = this.values.get(key);
        if (value != null) return (T) value;
        T created = key.createDefault();
        // A mutable default is kept, so changes made to it aren't lost.
        if (created instanceof Trackable) this.store(key, created);
        return created;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> void set(Attachment<T> key, T value) {
        Objects.requireNonNull(value, () -> "Can't set " + key.id() + " to null; use reset");
        T old = (T) this.values.get(key);
        if (isUnchanged(key, old, value)) return;
        Object outcome = AttachmentEventHooks.changing(this, key, old, value);
        if (outcome == AttachmentEventHooks.CANCELLED) return;
        T stored = (T) outcome;
        if (stored != value && isUnchanged(key, old, stored)) return;
        this.commit(key, old, stored);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> void modify(Attachment<T> key, Consumer<T> mutator) {
        T value = this.get(key);
        T old = this.values.containsKey(key) ? value : null;
        Object outcome = AttachmentEventHooks.changing(this, key, old, value);
        if (outcome == AttachmentEventHooks.CANCELLED) return;
        if (outcome != value) {
            // A listener handed a different value to store instead.
            this.commit(key, old, (T) outcome);
            return;
        }
        if (old == null) this.store(key, value);
        mutator.accept(value);
        AttachmentEventHooks.changed(this, key, old, value);
        this.markDirty(key);
    }

    // Setting what the holder already reads changes nothing.
    private static <T> boolean isUnchanged(Attachment<T> key, @Nullable T old, T value) {
        T current = old != null ? old : key.createDefault();
        return current != value && current.equals(value);
    }

    private <T> void commit(Attachment<T> key, @Nullable T old, T value) {
        this.store(key, value);
        AttachmentEventHooks.changed(this, key, old, value);
        this.markDirty(key);
    }

    @Override
    public void markDirty(Attachment<?> key) {
        Level level = this.level();
        if (level == null || level.isClientSide()) return;
        // What BlockEntity.setChanged does, without calling an override of it (which may mark data dirty in turn).
        if (this.owner instanceof BlockEntity blockEntity) {
            level.blockEntityChanged(blockEntity.getBlockPos());
            BlockState state = blockEntity.getBlockState();
            if (!state.isAir()) level.updateNeighbourForOutputSignal(blockEntity.getBlockPos(), state.getBlock());
        }
        if (key.isSynced()) AttachmentSync.queue(this, key);
    }

    @Override
    public boolean has(Attachment<?> key) {
        return this.values.containsKey(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void reset(Attachment<?> key) {
        Attachment<Object> raw = (Attachment<Object>) key;
        Object old = this.values.get(raw);
        if (old == null) return;
        Object outcome = AttachmentEventHooks.changing(this, raw, old, null);
        if (outcome == AttachmentEventHooks.CANCELLED) return;
        if (outcome != null) {
            // A listener handed a value to keep instead of removing it.
            if (!isUnchanged(raw, old, outcome)) this.commit(raw, old, outcome);
            return;
        }
        this.values.remove(raw);
        unbind(old);
        AttachmentEventHooks.changed(this, raw, old, null);
        this.markDirty(raw);
    }

    @Override
    public Set<Attachment<?>> attachments() {
        return Collections.unmodifiableSet(this.values.keySet());
    }

    private void store(Attachment<?> key, Object value) {
        Object old = this.values.put(key, value);
        if (old != value) {
            if (old != null) unbind(old);
            if (value instanceof Trackable trackable) trackable.setChangeListener(() -> this.trackableChanged(key));
        }
    }

    // A trackable value changed itself.
    @SuppressWarnings("unchecked")
    private void trackableChanged(Attachment<?> key) {
        Attachment<Object> raw = (Attachment<Object>) key;
        Object value = this.values.get(raw);
        if (value != null) AttachmentEventHooks.changed(this, raw, value, value);
        this.markDirty(key);
    }

    private static void unbind(Object value) {
        if (value instanceof Trackable trackable) trackable.setChangeListener(null);
    }

    // --- Sync ---

    boolean addDirty(Attachment<?> key) {
        return this.dirty.add(key);
    }

    List<Attachment<?>> takeDirty() {
        List<Attachment<?>> keys = new ArrayList<>(this.dirty);
        this.dirty.clear();
        return keys;
    }

    /** The synced keys with a stored value, for a full sync. */
    List<Attachment<?>> syncedKeys() {
        List<Attachment<?>> keys = new ArrayList<>();
        for (Attachment<?> key : this.values.keySet()) {
            if (key.isSynced()) keys.add(key);
        }
        return keys;
    }

    @Nullable Object rawValue(Attachment<?> key) {
        return this.values.get(key);
    }

    /** A value from the server; {@code null} resets it. Runs on the client. */
    @SuppressWarnings("unchecked")
    <T> void applySynced(Attachment<T> key, @Nullable T value) {
        T old = (T) this.values.get(key);
        if (value == null) {
            if (old == null) return;
            this.values.remove(key);
            unbind(old);
        } else {
            this.store(key, value);
        }
        AttachmentEventHooks.changed(this, key, old, value);
    }

    // --- Saving ---

    @SuppressWarnings("unchecked")
    public void save(ValueOutput output) {
        List<Map.Entry<Attachment<?>, Object>> saved = new ArrayList<>();
        for (Map.Entry<Attachment<?>, Object> entry : this.values.entrySet()) {
            Attachment<?> key = entry.getKey();
            if (!key.isPersistent()) continue;
            // An immutable default is what an absent value reads anyway.
            if (!(entry.getValue() instanceof Trackable) && entry.getValue().equals(key.createDefault())) continue;
            saved.add(entry);
        }
        if (saved.isEmpty()) return;
        // Each mod's values in a tag of its own: <modid>:attachments, by attachment name.
        Map<AttachmentRegistry, ValueOutput> children = new HashMap<>();
        for (Map.Entry<Attachment<?>, Object> entry : saved) {
            Attachment<Object> key = (Attachment<Object>) entry.getKey();
            ValueOutput child = children.computeIfAbsent(key.registry(), registry -> output.child(registry.tagName()));
            child.store(key.name(), Objects.requireNonNull(key.codec()), entry.getValue());
        }
    }

    /** Whether saved data holds any mod's attachments. */
    public static boolean hasSaved(ValueInput input) {
        for (AttachmentRegistry registry : AttachmentRegistry.all()) {
            if (input.child(registry.tagName()).isPresent()) return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public void load(ValueInput input) {
        for (AttachmentRegistry registry : AttachmentRegistry.all()) {
            input.child(registry.tagName()).ifPresent(child -> {
                for (Attachment<?> registered : registry.getAttachments()) {
                    Attachment<Object> key = (Attachment<Object>) registered;
                    Codec<Object> codec = key.codec();
                    if (codec != null) child.read(key.name(), codec).ifPresent(value -> this.store(key, value));
                }
            });
        }
        // Loaded into a block entity that's already in a level (a placed item's block entity data): tell the clients.
        Level level = this.level();
        if (level != null && !level.isClientSide()) {
            for (Attachment<?> key : this.syncedKeys()) AttachmentSync.queue(this, key);
        }
    }

    /**
     * Copies another store's values, deep-copying persistent ones through their codec. Transient values are shared,
     * except trackable ones, which can't have two owners.
     */
    @SuppressWarnings("unchecked")
    public void copyFrom(AttachmentStore other, Predicate<Attachment<?>> filter, HolderLookup.Provider registries) {
        DynamicOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        for (Map.Entry<Attachment<?>, Object> entry : other.values.entrySet()) {
            Attachment<Object> key = (Attachment<Object>) entry.getKey();
            if (!filter.test(key)) continue;
            Codec<Object> codec = key.codec();
            if (codec == null) {
                if (!(entry.getValue() instanceof Trackable)) this.store(key, entry.getValue());
                continue;
            }
            codec.encodeStart(ops, entry.getValue()).flatMap(tag -> codec.parse(ops, tag))
                    .resultOrPartial(error -> CoolCatCore.LOGGER.error("Couldn't copy {}: {}", key.id(), error))
                    .ifPresent(copy -> this.store(key, copy));
        }
    }
}
