package net.ixdarklord.coolcatcore.api.attachment;

import com.mojang.serialization.Codec;
import net.ixdarklord.coolcatcore.api.container.ContainerLayout;
import net.ixdarklord.coolcatcore.api.container.SlotContainer;
import net.ixdarklord.coolcatcore.internal.attachment.AttachmentIndex;
import net.minecraft.network.FriendlyByteBuf;
import net.ixdarklord.coolcatcore.api.network.codec.ByteBufCodecs;
import net.ixdarklord.coolcatcore.api.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * A typed piece of data any entity (players included), block entity or item stack can carry. Attachments come from a
 * mod's {@link AttachmentRegistry}, declared once, statically:
 * <pre>{@code
 * public static final AttachmentRegistry ATTACHMENTS = AttachmentRegistry.create(MOD_ID);
 *
 * // Saved with the player, sent only to that player, kept when they die.
 * public static final Attachment<Integer> MADNESS = ATTACHMENTS.builder("madness", Codec.INT, 0)
 *         .sync(SyncPolicy.SELF)
 *         .copyOnDeath()
 *         .build();
 *
 * int madness = MADNESS.get(player);
 * MADNESS.set(player, madness + 1);
 * MADNESS.update(player, m -> Math.max(0, m - 1));
 * }</pre>
 * Values should be immutable (numbers, records, immutable lists); a changed value is {@link #set} again. Mutable values
 * are allowed on entities and block entities: change them with {@link #modify}, or make them {@link Trackable}.
 * Item stacks only take immutable, persistent values.
 *
 * @param <T> the value's type
 */
public final class Attachment<T> {
    private final AttachmentRegistry registry;
    private final String name;
    private final ResourceLocation id;
    private final Supplier<T> defaultValue;
    private final @Nullable Codec<T> codec;
    private final @Nullable StreamCodec<? super FriendlyByteBuf, T> streamCodec;
    private final SyncPolicy syncPolicy;
    private final boolean copyOnDeath;
    private final boolean keepOnDrop;
    private final List<Listener<T>> listeners = new CopyOnWriteArrayList<>();

    private Attachment(Builder<T> builder) {
        this.registry = builder.registry;
        this.name = builder.name;
        this.id = builder.registry.id(builder.name);
        this.defaultValue = builder.defaultValue;
        this.codec = builder.codec;
        this.streamCodec = builder.streamCodec;
        this.syncPolicy = builder.syncPolicy;
        this.copyOnDeath = builder.copyOnDeath;
        this.keepOnDrop = builder.keepOnDrop;
        this.listeners.addAll(builder.listeners);
    }

    static <T> Builder<T> persistentBuilder(AttachmentRegistry registry, String name, Codec<T> codec, Supplier<T> defaultValue) {
        return new Builder<>(registry, name, defaultValue).persistent(codec);
    }

    static <T> Builder<T> transientBuilder(AttachmentRegistry registry, String name, Supplier<T> defaultValue) {
        return new Builder<>(registry, name, defaultValue);
    }

    static Builder<SlotContainer> containerBuilder(AttachmentRegistry registry, String name, ContainerLayout layout) {
        Builder<SlotContainer> builder = new Builder<>(registry, name, layout::create);
        builder.codec = layout.codec();
        builder.streamCodec = layout.streamCodec();
        builder.container = true;
        return builder;
    }

    /** The attachment with this id, if one was built. */
    public static @Nullable Attachment<?> byId(ResourceLocation id) {
        return AttachmentIndex.get(id);
    }

    /** {@code modid:name}. */
    public ResourceLocation id() {
        return this.id;
    }

    /** The name within its mod. */
    public String name() {
        return this.name;
    }

    /** The registry of the mod it belongs to; its values are stored under that mod's id. */
    public AttachmentRegistry registry() {
        return this.registry;
    }

    /** A fresh default value. */
    public T createDefault() {
        return Objects.requireNonNull(this.defaultValue.get(), () -> "The default of " + this.id + " is null");
    }

    /** The codec values are saved with, or {@code null} for a transient attachment. */
    public @Nullable Codec<T> codec() {
        return this.codec;
    }

    /** The codec values are synced with, or {@code null} if they never leave the server. */
    public @Nullable StreamCodec<? super FriendlyByteBuf, T> streamCodec() {
        return this.streamCodec;
    }

    public SyncPolicy syncPolicy() {
        return this.syncPolicy;
    }

    public boolean isPersistent() {
        return this.codec != null;
    }

    public boolean isSynced() {
        return this.syncPolicy != SyncPolicy.NONE && this.streamCodec != null;
    }

    /** Whether a player keeps the value when they die and respawn. */
    public boolean copyOnDeath() {
        return this.copyOnDeath;
    }

    /** Whether a block entity's value travels with its dropped item and comes back when it's placed. */
    public boolean keepOnDrop() {
        return this.keepOnDrop;
    }

    /**
     * Calls {@code listener} whenever a holder's value changes, on the side it changed: on the server when it's set,
     * on clients when a synced value arrives. For {@link #modify} and {@link Trackable} changes, the old and new
     * values are the same object.
     */
    public Attachment<T> onChange(Listener<T> listener) {
        this.listeners.add(listener);
        return this;
    }

    /** For holders: tells the listeners about a change. */
    public void fireChanged(AttachmentHolder holder, T oldValue, T newValue) {
        for (Listener<T> listener : this.listeners) listener.onChanged(holder, oldValue, newValue);
    }

    // --- Entities, players included ---

    public T get(Entity entity) {
        return AttachmentHolder.of(entity).get(this);
    }

    public void set(Entity entity, T value) {
        AttachmentHolder.of(entity).set(this, value);
    }

    /** Sets the value to {@code function}'s result and returns it. */
    public T update(Entity entity, UnaryOperator<T> function) {
        return AttachmentHolder.of(entity).update(this, function);
    }

    /** Changes a mutable value in place, then saves and syncs it. */
    public void modify(Entity entity, Consumer<T> mutator) {
        AttachmentHolder.of(entity).modify(this, mutator);
    }

    public boolean has(Entity entity) {
        return AttachmentHolder.of(entity).has(this);
    }

    /** Removes the value; the entity reads the default again. */
    public void reset(Entity entity) {
        AttachmentHolder.of(entity).reset(this);
    }

    // --- Block entities ---

    public T get(BlockEntity blockEntity) {
        return AttachmentHolder.of(blockEntity).get(this);
    }

    public void set(BlockEntity blockEntity, T value) {
        AttachmentHolder.of(blockEntity).set(this, value);
    }

    public T update(BlockEntity blockEntity, UnaryOperator<T> function) {
        return AttachmentHolder.of(blockEntity).update(this, function);
    }

    public void modify(BlockEntity blockEntity, Consumer<T> mutator) {
        AttachmentHolder.of(blockEntity).modify(this, mutator);
    }

    public boolean has(BlockEntity blockEntity) {
        return AttachmentHolder.of(blockEntity).has(this);
    }

    public void reset(BlockEntity blockEntity) {
        AttachmentHolder.of(blockEntity).reset(this);
    }

    // --- Item stacks ---

    public T get(ItemStack stack) {
        return AttachmentHolder.of(stack).get(this);
    }

    public void set(ItemStack stack, T value) {
        AttachmentHolder.of(stack).set(this, value);
    }

    public T update(ItemStack stack, UnaryOperator<T> function) {
        return AttachmentHolder.of(stack).update(this, function);
    }

    public boolean has(ItemStack stack) {
        return AttachmentHolder.of(stack).has(this);
    }

    public void reset(ItemStack stack) {
        AttachmentHolder.of(stack).reset(this);
    }

    @Override
    public String toString() {
        return "Attachment[" + this.id + "]";
    }

    @FunctionalInterface
    public interface Listener<T> {
        void onChanged(AttachmentHolder holder, T oldValue, T newValue);
    }

    public static final class Builder<T> {
        private final AttachmentRegistry registry;
        private final String name;
        private final Supplier<T> defaultValue;
        private @Nullable Codec<T> codec;
        private @Nullable StreamCodec<? super FriendlyByteBuf, T> streamCodec;
        private SyncPolicy syncPolicy = SyncPolicy.NONE;
        private boolean copyOnDeath;
        private boolean keepOnDrop;
        private boolean container;
        private final List<Listener<T>> listeners = new CopyOnWriteArrayList<>();

        private Builder(AttachmentRegistry registry, String name, Supplier<T> defaultValue) {
            this.registry = registry;
            this.name = Objects.requireNonNull(name, "name");
            this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        }

        private Builder<T> persistent(Codec<T> codec) {
            this.codec = Objects.requireNonNull(codec, "codec");
            return this;
        }

        /**
         * How values are written to the network. Persistent keys don't need one: their codec is used (through NBT),
         * but a dedicated stream codec is smaller and faster for values that change often.
         */
        public Builder<T> networkCodec(StreamCodec<? super FriendlyByteBuf, T> streamCodec) {
            this.streamCodec = Objects.requireNonNull(streamCodec, "streamCodec");
            return this;
        }

        /**
         * Sends the value to clients: whenever it changes (once per tick at most, only what changed), and in full
         * when a player starts seeing the holder, joins, respawns or changes dimension.
         */
        public Builder<T> sync(SyncPolicy policy) {
            this.syncPolicy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        /** Keeps a player's value through death (it's always kept through dimension changes and the End exit). */
        public Builder<T> copyOnDeath() {
            this.copyOnDeath = true;
            return this;
        }

        /**
         * Keeps a block entity's value in the item it drops as, and restores it when that item is placed (like a
         * shulker box's contents). The block's loot table must copy the value from the block entity into the item's
         * {@code BlockEntityTag}: add {@link AttachmentRegistry#copyKeptOnDrop()} to it (a {@code copy_nbt} function).
         */
        public Builder<T> keepOnDrop() {
            this.keepOnDrop = true;
            return this;
        }

        public Builder<T> onChange(Listener<T> listener) {
            this.listeners.add(Objects.requireNonNull(listener, "listener"));
            return this;
        }

        /** Creates the attachment and adds it to its registry. */
        public Attachment<T> build() {
            ResourceLocation id = this.registry.id(this.name);
            if (this.syncPolicy != SyncPolicy.NONE && this.streamCodec == null) {
                if (this.codec == null) {
                    throw new IllegalStateException("The synced transient attachment " + id + " needs a networkCodec");
                }
                this.streamCodec = ByteBufCodecs.fromCodecWithRegistries(this.codec);
            }
            if (this.keepOnDrop && (this.codec == null || this.container)) {
                throw new IllegalStateException("keepOnDrop needs an immutable persistent value; " + id
                        + " can't be stored on an item (containers keep their contents with ContainerLayout.keepContentsOnBreak)");
            }
            Attachment<T> attachment = new Attachment<>(this);
            this.registry.add(attachment);
            AttachmentIndex.register(attachment);
            return attachment;
        }
    }
}
