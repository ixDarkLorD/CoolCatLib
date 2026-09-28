package net.ixdarklord.coolcatcore.api.block;

import net.ixdarklord.coolcatcore.api.attachment.Attachment;
import net.ixdarklord.coolcatcore.api.attachment.AttachmentHolder;
import net.ixdarklord.coolcatcore.api.container.ContainerLayout;
import net.ixdarklord.coolcatcore.api.container.ItemContainers;
import net.ixdarklord.coolcatcore.api.container.SidedContainerView;
import net.ixdarklord.coolcatcore.api.container.SlotContainer;
import net.ixdarklord.coolcatcore.api.handler.HandlerType;
import net.ixdarklord.coolcatcore.api.handler.HandlerTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link ExtendedBlockEntity} with an inventory. The inventory is a {@link net.ixdarklord.coolcatcore.api.attachment.AttachmentRegistry#container container attachment}, so it
 * saves and syncs like the rest of the data; the block entity itself is a {@link WorldlyContainer}, so hoppers,
 * comparators, menus, and other mods' pipes (on both loaders) use it through the layout's faces and roles.
 * <pre>{@code
 * public static final Attachment<SlotContainer> INVENTORY = MyMod.ATTACHMENTS.container("crusher_inventory", CRUSHER_LAYOUT).build();
 *
 * public CrusherBlockEntity(BlockPos pos, BlockState state) {
 *     super(MyBlockEntities.CRUSHER.get(), pos, state, INVENTORY);
 * }
 * }</pre>
 * The contents spill when the block is broken, unless the layout {@link ContainerLayout#keepContentsOnBreak keeps them};
 * on 1.20.1 the block does that from its {@code onRemove}, with {@link #onBlockRemoved}.
 * Add {@code .sync(SyncPolicy.ALL)} to the attachment when clients need the contents without a menu open (to render them).
 */
public abstract class ExtendedContainerBlockEntity extends ExtendedBlockEntity implements WorldlyContainer {
    private final Attachment<SlotContainer> containerAttachment;

    protected ExtendedContainerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, Attachment<SlotContainer> containerAttachment) {
        super(type, pos, state);
        this.containerAttachment = containerAttachment;
    }

    public SlotContainer getContainer() {
        return this.get(this.containerAttachment);
    }

    public Attachment<SlotContainer> getContainerAttachment() {
        return this.containerAttachment;
    }

    public ContainerLayout getLayout() {
        return this.getContainer().layout();
    }

    /** The comparator signal of the contents, for the block's {@code getAnalogOutputSignal}. */
    public int getComparatorSignal() {
        return this.getContainer().comparatorSignal();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> @Nullable T getHandler(HandlerType<T> type, @Nullable Direction side) {
        T handler = super.getHandler(type, side);
        if (handler != null || type != HandlerTypes.CONTAINER) return handler;
        return (T) (side == null ? this : new SidedContainerView(this, side));
    }

    // Menus sometimes change a stack in place and then only call setChanged: sync the container then too.
    @Override
    public void setChanged() {
        super.setChanged();
        if (this.containerAttachment.isSynced()) this.attachments().markDirty(this.containerAttachment);
    }

    /**
     * For the block's {@code onRemove}, before calling {@code super.onRemove}: when the block is replaced by another,
     * spills the contents of its {@link ExtendedContainerBlockEntity} (unless the layout
     * {@link ContainerLayout#keepContentsOnBreak keeps them}) and updates comparators.
     * <pre>{@code
     * public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
     *     ExtendedContainerBlockEntity.onBlockRemoved(state, level, pos, newState);
     *     super.onRemove(state, level, pos, newState, movedByPiston);
     * }
     * }</pre>
     */
    public static void onBlockRemoved(BlockState state, Level level, BlockPos pos, BlockState newState) {
        if (state.is(newState.getBlock())) return;
        if (level.getBlockEntity(pos) instanceof ExtendedContainerBlockEntity blockEntity) {
            if (!blockEntity.getLayout().keepContentsOnBreak()) Containers.dropContents(level, pos, blockEntity);
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
    }

    // A kept inventory is also saved as a vanilla Items list, for the loot table to copy into the dropped item, from
    // where placing the block loads it back.
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (this.getLayout().keepContentsOnBreak() && !this.isEmpty()) {
            tag.put(ItemContainers.ITEMS_TAG, this.getContainer().toContents().get(ItemContainers.ITEMS_TAG));
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (this.getLayout().keepContentsOnBreak() && tag.contains(ItemContainers.ITEMS_TAG, Tag.TAG_LIST)) {
            this.getContainer().fromContents(tag);
        }
    }

    // --- WorldlyContainer ---

    @Override
    public int[] getSlotsForFace(Direction side) {
        return this.getLayout().slotsForFace(side);
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return this.getContainer().canInsertFrom(slot, stack, side);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return this.getContainer().canExtractFrom(slot, side);
    }

    @Override
    public int getContainerSize() {
        return this.getContainer().getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        return this.getContainer().isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.getContainer().getItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        return this.getContainer().removeItem(slot, count);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return this.getContainer().removeItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.getContainer().setItem(slot, stack);
    }

    @Override
    public int getMaxStackSize() {
        return this.getContainer().getMaxStackSize();
    }

    /** The most of the item a slot holds, before per-slot limits. */
    public int getMaxStackSize(ItemStack stack) {
        return this.getContainer().getMaxStackSize(stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player) && this.getContainer().stillValid(player);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return this.getContainer().canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItem(Container into, int slot, ItemStack stack) {
        return this.getContainer().canTakeItem(into, slot, stack);
    }

    @Override
    public void clearContent() {
        this.getContainer().clearContent();
    }
}
