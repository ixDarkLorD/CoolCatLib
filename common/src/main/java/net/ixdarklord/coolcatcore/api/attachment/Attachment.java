package net.ixdarklord.coolcatcore.api.attachment;

import com.mojang.serialization.Codec;
import net.ixdarklord.coolcatcore.api.container.ContainerLayout;
import net.ixdarklord.coolcatcore.api.container.SlotContainer;
import net.ixdarklord.coolcatcore.internal.attachment.AttachmentIndex;
import net.ixdarklord.coolcatcore.internal.attachment.OfflineAttachments;
import net.minecraft.network.FriendlyByteBuf;
import net.ixdarklord.coolcatcore.api.network.codec.ByteBufCodecs;
import net.ixdarklord.coolcatcore.api.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * A typed piece of data any entity (players included), block entity, item stack, level or the server itself can carry.
 * Attachments come from a mod's {@link AttachmentRegistry}, declared once, statically:
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
 * are allowed on entities, block entities, levels and the server: change them with {@link #modify}, or make them
 * {@link Trackable}. Item stacks only take immutable, persistent values.
 * <p>
 * A level's values ({@link #get(Level)}) are saved in its data folder and synced to the players in it; the server's
 * ({@link #get(MinecraftServer)}) with the overworld's, synced to every player, whom {@link #getGlobal} lets read them.
 * For both, {@link SyncPolicy#ALL} sends them and {@link SyncPolicy#NONE} keeps them on the server. A player's values
 * can be read and written while they're offline too ({@link #getOffline}).
 * <pre>{@code
 * TEAM_STAGE.set(server, 3);                          // the whole server's, saved with it
 * int stage = TEAM_STAGE.getGlobal(level);             // on either side: the server's, or the client's synced copy
 * MADNESS.updateOffline(server, uuid, m -> m + 1);    // a player who isn't online: written to their data file
 * }</pre>
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
    private final boolean swapAnimation;
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
        this.swapAnimation = builder.swapAnimation;
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

    /**
     * Whether a change to its value on a held item shows the item being put away and taken out again, as vanilla does
     * for most changes to a held stack. Off unless the builder asked for it ({@link Builder#swapAnimation()}): an
     * attachment's value changing leaves the item where it is in the hand (see {@code HeldItemSwaps}).
     */
    public boolean playsSwapAnimation() {
        return this.swapAnimation;
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

    // --- Levels: a server level's saved in its data folder, a client level's as the server sent them ---

    public T get(Level level) {
        return AttachmentHolder.of(level).get(this);
    }

    public void set(Level level, T value) {
        AttachmentHolder.of(level).set(this, value);
    }

    public T update(Level level, UnaryOperator<T> function) {
        return AttachmentHolder.of(level).update(this, function);
    }

    public void modify(Level level, Consumer<T> mutator) {
        AttachmentHolder.of(level).modify(this, mutator);
    }

    public boolean has(Level level) {
        return AttachmentHolder.of(level).has(this);
    }

    public void reset(Level level) {
        AttachmentHolder.of(level).reset(this);
    }

    // --- The server, saved in the overworld's data folder ---

    public T get(MinecraftServer server) {
        return AttachmentHolder.of(server).get(this);
    }

    public void set(MinecraftServer server, T value) {
        AttachmentHolder.of(server).set(this, value);
    }

    public T update(MinecraftServer server, UnaryOperator<T> function) {
        return AttachmentHolder.of(server).update(this, function);
    }

    public void modify(MinecraftServer server, Consumer<T> mutator) {
        AttachmentHolder.of(server).modify(this, mutator);
    }

    public boolean has(MinecraftServer server) {
        return AttachmentHolder.of(server).has(this);
    }

    public void reset(MinecraftServer server) {
        AttachmentHolder.of(server).reset(this);
    }

    /**
     * The server's value as this side knows it: on the server, its own ({@link #get(MinecraftServer)}); on a client,
     * the copy it was sent (the default if this attachment isn't synced).
     *
     * @param level any level of the side asking
     */
    public T getGlobal(Level level) {
        return AttachmentHolder.global(level).get(this);
    }

    // --- Offline players: read from and written to their data file (an online player's are their entity's) ---

    /**
     * A player's value whether or not they're online: an online player's from their entity, an offline one's from their
     * data file, or the default for a player who has never played here. Only for saved attachments. On the server
     * thread.
     */
    public T getOffline(MinecraftServer server, UUID player) {
        return OfflineAttachments.get(server, player, this);
    }

    /**
     * Sets a player's value whether or not they're online; an offline player's is written to their data file at once
     * (no {@code AttachmentEvents} fire for it, and it reaches them when they next join). Only for saved attachments. On
     * the server thread.
     *
     * @return false if the player has never played here, or the file couldn't be written
     */
    public boolean setOffline(MinecraftServer server, UUID player, T value) {
        return OfflineAttachments.set(server, player, this, value);
    }

    /** Sets a player's value to {@code function}'s result, whether or not they're online (see {@link #setOffline}). */
    public boolean updateOffline(MinecraftServer server, UUID player, UnaryOperator<T> function) {
        return OfflineAttachments.update(server, player, this, function);
    }

    /** Removes a player's value whether or not they're online (see {@link #setOffline}). */
    public boolean resetOffline(MinecraftServer server, UUID player) {
        return OfflineAttachments.set(server, player, this, null);
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
        private boolean swapAnimation;
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

        /**
         * Shows a held item being put away and taken out again whenever this value changes on it, as vanilla does for
         * most changes to a held stack. Without it, attachment changes are quiet: the item stays where it is.
         */
        public Builder<T> swapAnimation() {
            this.swapAnimation = true;
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
