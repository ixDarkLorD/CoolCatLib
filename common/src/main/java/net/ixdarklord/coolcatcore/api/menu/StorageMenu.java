package net.ixdarklord.coolcatcore.api.menu;

import io.netty.buffer.Unpooled;
import net.ixdarklord.coolcatcore.api.attachment.Attachment;
import net.ixdarklord.coolcatcore.api.attachment.AttachmentHolder;
import net.ixdarklord.coolcatcore.api.block.ExtendedContainerBlockEntity;
import net.ixdarklord.coolcatcore.api.container.ContainerItem;
import net.ixdarklord.coolcatcore.api.container.ContainerLayout;
import net.ixdarklord.coolcatcore.api.container.SlotContainer;
import net.ixdarklord.coolcatcore.api.network.Network;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.ixdarklord.coolcatcore.internal.menu.StorageMenuValuesPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The menu a {@link StorageMenuDefinition} opens on a block's or an item's inventory. On the server its slots are the
 * real container; on the client, a copy vanilla keeps in sync. Screens read synced values with {@link #get(Attachment)}
 * and {@link #getValue(String, Object)}.
 */
public class StorageMenu extends AbstractContainerMenu {
    private final StorageMenuDefinition definition;
    private final Player player;
    private final Source source;
    private final Container container;
    private final @Nullable Container opened;
    private final int containerSlotCount;
    private final int lockedSlot;
    // Server: the bytes last sent per value. Client: the values received.
    private final Map<String, byte[]> sent = new HashMap<>();
    private final Map<String, Object> received = new HashMap<>();

    protected StorageMenu(StorageMenuDefinition definition, int containerId, Inventory inventory, Source source, Container container, @Nullable Container opened) {
        super(definition.menuType(), containerId);
        this.definition = definition;
        this.player = inventory.player;
        this.source = source;
        this.container = container;
        this.opened = opened;

        for (StorageMenuDefinition.SlotPlacement placement : definition.slots()) {
            if (placement.containerSlot() < 0 || placement.containerSlot() >= container.getContainerSize()) continue;
            this.addSlot(container instanceof SlotContainer slots
                    ? slots.createSlot(placement.containerSlot(), placement.x(), placement.y())
                    : new Slot(container, placement.containerSlot(), placement.x(), placement.y()));
        }
        this.containerSlotCount = this.slots.size();

        // The player's inventory; the slot holding an opened item can't be touched.
        int locked = -1;
        for (int index = 0; index < 36; index++) {
            int x = definition.inventoryX() + index % 9 * 18;
            int y = index < 9 ? definition.inventoryY() + 58 : definition.inventoryY() + (index / 9 - 1) * 18;
            if (source.isItem() && index == source.inventorySlot()) {
                locked = this.slots.size();
                this.addSlot(new LockedSlot(inventory, index, x, y));
            } else {
                this.addSlot(new Slot(inventory, index, x, y));
            }
        }
        this.lockedSlot = locked;
        if (this.opened != null) this.opened.startOpen(this.player);
    }

    static StorageMenu readClient(StorageMenuDefinition definition, int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        Source source = Source.read(data, inventory);
        int size = data.readVarInt();
        // The client builds the same layout from its own copy of the block entity or stack, when it has one.
        ContainerLayout layout = null;
        if (source.blockEntity() instanceof ExtendedContainerBlockEntity holder) layout = holder.getLayout();
        else if (source.stack() != null && source.stack().getItem() instanceof ContainerItem item) layout = item.containerLayout(source.stack());
        if (layout == null || layout.size() != size) layout = ContainerLayout.of(size);
        return new StorageMenu(definition, containerId, inventory, source, layout.create(), null);
    }

    public StorageMenuDefinition getDefinition() {
        return this.definition;
    }

    public Player getPlayer() {
        return this.player;
    }

    public Source getSource() {
        return this.source;
    }

    /** The inventory the slots show: the real one on the server, the synced copy on the client. */
    public Container getContainer() {
        return this.container;
    }

    /** The number of container slots before the player's inventory slots. */
    public int getContainerSlotCount() {
        return this.containerSlotCount;
    }

    /** The attachments of what the menu is open on: the block entity or the stack. */
    public AttachmentHolder attachments() {
        if (this.source.blockEntity() != null) return AttachmentHolder.of(this.source.blockEntity());
        if (this.source.stack() != null) return AttachmentHolder.of(this.source.stack());
        throw new IllegalStateException("The menu's block entity isn't loaded here");
    }

    // --- Synced values ---

    /**
     * An attachment of the block entity or stack: on clients, the value the menu synced (if its definition syncs it),
     * or else the one the client has.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Attachment<T> attachment) {
        String name = attachment.id().toString();
        if (this.player.level().isClientSide() && this.definition.values().containsKey(name)) {
            return (T) this.received.getOrDefault(name, attachment.createDefault());
        }
        return this.attachments().get(attachment);
    }

    /** A value synced with {@link StorageMenuDefinition.Builder#syncValue}: read on the server, received on clients. */
    @SuppressWarnings("unchecked")
    public <T> T getValue(String name, T defaultValue) {
        StorageMenuDefinition.SyncedValue<T> value = (StorageMenuDefinition.SyncedValue<T>) this.definition.values().get(name);
        if (value == null) return defaultValue;
        if (this.player.level().isClientSide()) return (T) this.received.getOrDefault(name, defaultValue);
        return value.getter().apply(this);
    }

    @Override
    public void sendAllDataToRemote() {
        super.sendAllDataToRemote();
        this.sent.clear();
        this.sendValues();
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        this.sendValues();
    }

    @Override
    public void broadcastFullState() {
        super.broadcastFullState();
        this.sent.clear();
        this.sendValues();
    }

    // Changes are found by comparing encodings, so values changed in place are noticed too.
    private void sendValues() {
        if (!(this.player instanceof ServerPlayer serverPlayer) || this.definition.values().isEmpty()) return;
        List<StorageMenuValuesPayload.Entry> entries = new ArrayList<>();
        for (StorageMenuDefinition.SyncedValue<?> value : this.definition.values().values()) {
            byte[] bytes = this.encode(value, serverPlayer.registryAccess());
            if (bytes == null || Arrays.equals(this.sent.get(value.name()), bytes)) continue;
            this.sent.put(value.name(), bytes);
            entries.add(new StorageMenuValuesPayload.Entry(value.name(), bytes));
        }
        if (!entries.isEmpty()) Network.sendToPlayer(serverPlayer, new StorageMenuValuesPayload(this.containerId, entries));
    }

    private <T> byte @Nullable [] encode(StorageMenuDefinition.SyncedValue<T> value, RegistryAccess registries) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            value.codec().encode(buf, Objects.requireNonNull(value.getter().apply(this), () -> value.name() + " is null"));
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return bytes;
        } catch (RuntimeException e) {
            CoolCatCore.LOGGER.error("Couldn't encode the menu value {}", value.name(), e);
            return null;
        } finally {
            buf.release();
        }
    }

    /** Values from the server. Runs on the client. */
    public void receiveValues(List<StorageMenuValuesPayload.Entry> entries, RegistryAccess registries) {
        for (StorageMenuValuesPayload.Entry entry : entries) {
            StorageMenuDefinition.SyncedValue<?> value = this.definition.values().get(entry.name());
            if (value != null) this.decode(value, entry.value(), registries);
        }
        this.onValuesReceived();
    }

    private <T> void decode(StorageMenuDefinition.SyncedValue<T> value, byte[] bytes, RegistryAccess registries) {
        StreamCodec<? super RegistryFriendlyByteBuf, T> codec = value.codec();
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), registries);
        try {
            this.received.put(value.name(), codec.decode(buf));
        } catch (RuntimeException e) {
            CoolCatCore.LOGGER.error("Couldn't read the menu value {}", value.name(), e);
        } finally {
            buf.release();
        }
    }

    /** Called on the client after synced values arrived. */
    protected void onValuesReceived() {
    }

    // --- Menu ---

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        var action = this.definition.button(buttonId);
        if (action == null || !(player instanceof ServerPlayer serverPlayer)) return false;
        action.accept(this, serverPlayer);
        return true;
    }

    @Override
    public void clicked(int slotIndex, int buttonNum, ContainerInput input, Player player) {
        // An opened item stays where it is: its slot can't be clicked, and it can't be swapped out with number keys.
        if (this.source.isItem()) {
            if (slotIndex >= 0 && slotIndex == this.lockedSlot) return;
            if (input == ContainerInput.SWAP && buttonNum == this.source.inventorySlot()) return;
        }
        super.clicked(slotIndex, buttonNum, input, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        // Container slots go to the player (hotbar first); the player's go into the container.
        boolean moved = slotIndex < this.containerSlotCount
                ? this.moveItemStackTo(stack, this.containerSlotCount, this.slots.size(), true)
                : this.moveItemStackTo(stack, 0, this.containerSlotCount, false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        if (player.level().isClientSide()) return true;
        if (this.source.isItem()) {
            ItemStack stack = player.getInventory().getItem(this.source.inventorySlot());
            return stack == this.source.stack() && !stack.isEmpty() && this.container.stillValid(player);
        }
        BlockEntity blockEntity = this.source.blockEntity();
        return blockEntity != null && !blockEntity.isRemoved() && blockEntity.getLevel() != null
                && blockEntity.getLevel().getBlockEntity(blockEntity.getBlockPos()) == blockEntity
                && Container.stillValidBlockEntity(blockEntity, player) && this.container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (this.opened != null) this.opened.stopOpen(player);
    }

    /**
     * What a menu is open on: a block entity (by position), or the stack in one of the player's inventory slots.
     * On the client, the block entity or stack is the client's own copy.
     */
    public record Source(@Nullable BlockPos pos, @Nullable BlockEntity blockEntity, int inventorySlot, @Nullable ItemStack stack) {
        static Source block(BlockEntity blockEntity) {
            return new Source(blockEntity.getBlockPos(), blockEntity, -1, null);
        }

        static Source item(int inventorySlot, ItemStack stack) {
            return new Source(null, null, inventorySlot, stack);
        }

        public boolean isItem() {
            return this.pos == null;
        }

        void write(RegistryFriendlyByteBuf buf, int containerSize) {
            buf.writeBoolean(this.pos != null);
            if (this.pos != null) BlockPos.STREAM_CODEC.encode(buf, this.pos);
            else buf.writeVarInt(this.inventorySlot);
            buf.writeVarInt(containerSize);
        }

        static Source read(RegistryFriendlyByteBuf buf, Inventory inventory) {
            if (buf.readBoolean()) {
                BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                return new Source(pos, inventory.player.level().getBlockEntity(pos), -1, null);
            }
            int slot = buf.readVarInt();
            return new Source(null, null, slot, inventory.getItem(slot));
        }
    }

    // The slot of the opened item: shown, but never taken or replaced.
    private static final class LockedSlot extends Slot {
        LockedSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
