package net.ixdarklord.coolcatcore.api.container;

import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * What a {@link WorldlyContainer} shows through one face: only the slots that face reaches (numbered from 0), and
 * only what the face allows in and out. Taking from a slot the face can't take from gives nothing.
 */
public final class SidedContainerView implements Container {
    private final WorldlyContainer container;
    private final Direction side;
    private final int[] slots;

    public SidedContainerView(WorldlyContainer container, Direction side) {
        this.container = container;
        this.side = side;
        this.slots = container.getSlotsForFace(side);
    }

    public WorldlyContainer getContainer() {
        return this.container;
    }

    public Direction getSide() {
        return this.side;
    }

    /** The underlying container's slot for one of this view's slots. */
    public int getContainerSlot(int slot) {
        return this.slots[slot];
    }

    @Override
    public int getContainerSize() {
        return this.slots.length;
    }

    @Override
    public boolean isEmpty() {
        for (int slot : this.slots) {
            if (!this.container.getItem(slot).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < this.slots.length ? this.container.getItem(this.slots[slot]) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        if (!this.canTake(slot)) return ItemStack.EMPTY;
        return this.container.removeItem(this.slots[slot], count);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (!this.canTake(slot)) return ItemStack.EMPTY;
        return this.container.removeItemNoUpdate(this.slots[slot]);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < this.slots.length) this.container.setItem(this.slots[slot], stack);
    }

    @Override
    public int getMaxStackSize() {
        return this.container.getMaxStackSize();
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return this.container.getMaxStackSize(stack);
    }

    @Override
    public void setChanged() {
        this.container.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= this.slots.length) return false;
        int target = this.slots[slot];
        return this.container.canPlaceItem(target, stack) && this.container.canPlaceItemThroughFace(target, stack, this.side);
    }

    @Override
    public boolean canTakeItem(Container into, int slot, ItemStack stack) {
        if (slot < 0 || slot >= this.slots.length) return false;
        int target = this.slots[slot];
        return this.container.canTakeItem(into, target, stack) && this.container.canTakeItemThroughFace(target, stack, this.side);
    }

    @Override
    public void clearContent() {
        for (int slot : this.slots) this.container.setItem(slot, ItemStack.EMPTY);
    }

    private boolean canTake(int slot) {
        return slot >= 0 && slot < this.slots.length
                && this.container.canTakeItemThroughFace(this.slots[slot], this.container.getItem(this.slots[slot]), this.side);
    }
}
