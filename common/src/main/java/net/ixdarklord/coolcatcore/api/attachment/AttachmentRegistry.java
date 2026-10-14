package net.ixdarklord.coolcatcore.api.attachment;

import com.mojang.serialization.Codec;
import net.ixdarklord.coolcatcore.api.container.ContainerLayout;
import net.ixdarklord.coolcatcore.api.container.SlotContainer;
import net.ixdarklord.coolcatcore.api.registry.DeferredRegister;
import net.ixdarklord.coolcatcore.api.registry.RegistryEntry;
import net.ixdarklord.coolcatcore.internal.attachment.AttachmentBundle;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;
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
 * One mod's {@link Attachment}s. A mod's values are stored under its own id: in the {@code <modid>:attachments} item
 * component on stacks, and in a {@code <modid>:attachments} tag in entity and block entity data, so each mod's data
 * stays separate from every other's.
 * <pre>{@code
 * public static final AttachmentRegistry ATTACHMENTS = AttachmentRegistry.create(MOD_ID);
 * public static final Attachment<Integer> MADNESS = ATTACHMENTS.builder("madness", Codec.INT, 0).build();
 *
 * // While the mod initialises (it registers the item component, like a DeferredRegister):
 * ATTACHMENTS.register();
 * }</pre>
 */
public final class AttachmentRegistry {
    private static final Map<String, AttachmentRegistry> BY_MOD = new ConcurrentHashMap<>();

    private final String modId;
    private final String tagName;
    private final Map<String, Attachment<?>> attachments = Collections.synchronizedMap(new LinkedHashMap<>());
    private final DeferredRegister<DataComponentType<?>> components;
    private final RegistryEntry<DataComponentType<AttachmentBundle>> component;
    private boolean registered;

    private AttachmentRegistry(String modId) {
        this.modId = modId;
        this.tagName = modId + ":attachments";
        this.components = DeferredRegister.create(modId, Registries.DATA_COMPONENT_TYPE);
        this.component = this.components.register("attachments", () -> {
            Codec<AttachmentBundle> codec = AttachmentBundle.codec(this);
            return DataComponentType.<AttachmentBundle>builder()
                    .persistent(codec)
                    .networkSynchronized(AttachmentBundle.streamCodec(codec))
                    .build();
        });
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

    /** Registers the mod's item component. Call once, while the mod initialises. */
    public void register() {
        if (this.registered) return;
        this.registered = true;
        this.components.register();
    }

    // --- Looking up ---

    public String modId() {
        return this.modId;
    }

    /** {@code modid:name}. */
    public ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(this.modId, name);
    }

    public @Nullable Attachment<?> get(String name) {
        return this.attachments.get(name);
    }

    public Collection<Attachment<?>> getAttachments() {
        synchronized (this.attachments) {
            return List.copyOf(this.attachments.values());
        }
    }

    /** The name of the tag holding this mod's values in entity and block entity data. */
    public String tagName() {
        return this.tagName;
    }

    /** The {@code <modid>:attachments} item component holding this mod's values on stacks. */
    public DataComponentType<?> componentType() {
        return this.bundleComponent();
    }

    /** Whether {@link #register()} was called, so the item component exists. */
    public boolean isRegistered() {
        return this.registered;
    }

    @ApiStatus.Internal
    public DataComponentType<AttachmentBundle> bundleComponent() {
        if (!this.registered || !this.component.isBound()) {
            throw new IllegalStateException("The attachment registry of " + this.modId
                    + " isn't registered; call register() while the mod initialises to store attachments on items");
        }
        return this.component.get();
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
