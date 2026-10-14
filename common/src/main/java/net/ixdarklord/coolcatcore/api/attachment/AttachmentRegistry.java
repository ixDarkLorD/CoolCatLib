package net.ixdarklord.coolcatcore.api.attachment;

import com.mojang.serialization.Codec;
import net.ixdarklord.coolcatcore.api.container.ContainerLayout;
import net.ixdarklord.coolcatcore.api.container.SlotContainer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.functions.CopyNbtFunction;
import net.minecraft.world.level.storage.loot.providers.nbt.ContextNbtProvider;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * One mod's {@link Attachment}s. A mod's values are stored under its own id: in a {@code <modid>:attachments} tag in
 * a stack's NBT, and in entity and block entity data, so each mod's data stays separate from every other's.
 * <pre>{@code
 * public static final AttachmentRegistry ATTACHMENTS = AttachmentRegistry.create(MOD_ID);
 * public static final Attachment<Integer> MADNESS = ATTACHMENTS.builder("madness", Codec.INT, 0).build();
 *
 * // While the mod initialises:
 * ATTACHMENTS.register();
 * }</pre>
 */
public final class AttachmentRegistry {
    private static final Map<String, AttachmentRegistry> BY_MOD = new ConcurrentHashMap<>();

    private final String modId;
    private final String tagName;
    private final Map<String, Attachment<?>> attachments = Collections.synchronizedMap(new LinkedHashMap<>());
    private boolean registered;

    private AttachmentRegistry(String modId) {
        this.modId = modId;
        this.tagName = modId + ":attachments";
    }

    /** The registry of a mod; each mod creates one. */
    public static AttachmentRegistry create(String modId) {
        Objects.requireNonNull(modId, "modId");
        AttachmentRegistry registry = new AttachmentRegistry(modId);
        if (BY_MOD.putIfAbsent(modId, registry) != null) {
            throw new IllegalArgumentException("The mod " + modId + " already has an attachment registry");
        }
        return registry;
    }

    /** Every mod's registry. */
    public static Collection<AttachmentRegistry> all() {
        return Collections.unmodifiableCollection(BY_MOD.values());
    }

    public static @Nullable AttachmentRegistry forMod(String modId) {
        return BY_MOD.get(modId);
    }

    // --- Declaring ---

    /**
     * An attachment saved with its holder. {@code defaultValue} is what holders without a value read; it must be
     * immutable. For a mutable default, use {@link #mutableBuilder}.
     */
    public <T> Attachment.Builder<T> builder(String name, Codec<T> codec, T defaultValue) {
        Objects.requireNonNull(defaultValue, "defaultValue");
        return Attachment.persistentBuilder(this, name, codec, () -> defaultValue);
    }

    /**
     * An attachment saved with its holder, whose default is made fresh for each holder: for mutable values, changed
     * with {@link Attachment#modify} (or {@link Trackable} ones). Not usable on item stacks.
     */
    public <T> Attachment.Builder<T> mutableBuilder(String name, Codec<T> codec, Supplier<T> defaultValue) {
        return Attachment.persistentBuilder(this, name, codec, defaultValue);
    }

    /**
     * An attachment that's never saved: its value is gone when the holder unloads (timers, caches, per-session state).
     * It can still be synced with {@link Attachment.Builder#networkCodec} and {@link Attachment.Builder#sync}. Not
     * usable on item stacks.
     */
    public <T> Attachment.Builder<T> transientBuilder(String name, Supplier<T> defaultValue) {
        return Attachment.transientBuilder(this, name, defaultValue);
    }

    /**
     * An inventory as an attachment, so any entity or block entity can carry one (a player's backpack slots, a mob's
     * loot). Its slot changes save and sync on their own.
     */
    public Attachment.Builder<SlotContainer> container(String name, ContainerLayout layout) {
        return Attachment.containerBuilder(this, name, layout);
    }

    /**
     * Call once, while the mod initialises. On 1.20.1, item stacks keep their values in their NBT, so there's no item
     * component to register; this only marks the registry ready, and is kept so code stays the same across versions.
     */
    public void register() {
        this.registered = true;
    }

    // --- Looking up ---

    public String modId() {
        return this.modId;
    }

    /** {@code modid:name}. */
    public ResourceLocation id(String name) {
        return new ResourceLocation(this.modId, name);
    }

    public @Nullable Attachment<?> get(String name) {
        return this.attachments.get(name);
    }

    public Collection<Attachment<?>> getAttachments() {
        synchronized (this.attachments) {
            return List.copyOf(this.attachments.values());
        }
    }

    /**
     * The name of the tag ({@code <modid>:attachments}) holding this mod's values: in entity and block entity data,
     * and in item stacks' NBT.
     */
    public String tagName() {
        return this.tagName;
    }

    /** Whether {@link #register()} was called. */
    public boolean isRegistered() {
        return this.registered;
    }

    /**
     * A loot function for a block's loot table that copies this mod's {@link Attachment.Builder#keepOnDrop keepOnDrop}
     * values from the broken block entity into the dropped item, for placing it to restore them:
     * <pre>{@code
     * LootItem.lootTableItem(block).apply(MyMod.ATTACHMENTS.copyKeptOnDrop())
     * }</pre>
     * In a JSON loot table, it's a {@code minecraft:copy_nbt} function from {@code block_entity}, with one
     * {@code "op": "replace"} operation per value: from {@code "<modid>:attachments"."<name>"} to
     * {@code BlockEntityTag."<modid>:attachments"."<name>"}.
     */
    public CopyNbtFunction.Builder copyKeptOnDrop() {
        CopyNbtFunction.Builder builder = CopyNbtFunction.copyData(ContextNbtProvider.BLOCK_ENTITY);
        for (Attachment<?> attachment : this.getAttachments()) {
            if (!attachment.keepOnDrop()) continue;
            String path = "\"" + this.tagName + "\".\"" + attachment.name() + "\"";
            builder.copy(path, "BlockEntityTag." + path);
        }
        return builder;
    }

    void add(Attachment<?> attachment) {
        if (this.attachments.putIfAbsent(attachment.name(), attachment) != null) {
            throw new IllegalArgumentException("The attachment " + attachment.id() + " already exists");
        }
    }

    @Override
    public String toString() {
        return "AttachmentRegistry[" + this.modId + "]";
    }
}
