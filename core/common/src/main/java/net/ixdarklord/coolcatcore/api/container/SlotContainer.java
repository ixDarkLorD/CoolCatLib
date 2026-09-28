package net.ixdarklord.coolcatcore.api.container;

import net.ixdarklord.coolcatcore.api.attachment.Trackable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * An inventory following a {@link ContainerLayout}. It's a vanilla {@link Container}, so menus, hoppers and
 * {@link net.minecraft.world.Containers} work with it as they are; use {@link #createSlot} for menu slots that respect
 * the layout's filters, limits and roles.
 * <p>
 * Stored as a {@link net.ixdarklord.coolcatcore.api.attachment.Attachment#container Attachment}, its changes save and sync on their
 * own. {@link net.ixdarklord.coolcatcore.api.container.ItemContainers ItemContainers} binds one to an item stack.
 */
public class SlotContainer implements Container, Trackable {
    private final ContainerLayout layout;
    private final NonNullList<ItemStack> items;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private @Nullable Runnable changeListener;
    private Predicate<Player> validator = player -> true;

    public SlotContainer(ContainerLayout layout) {
        this.layout = layout;
        this.items = NonNullList.withSize(layout.size(), ItemStack.EMPTY);
    }

    public ContainerLayout layout() {
        return this.layout;
    }

    // --- Container ---

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : this.items) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < this.items.size() ? this.items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack removed = ContainerHelper.removeItem(this.items, slot, count);
        if (!removed.isEmpty()) this.changed(slot);
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = ContainerHelper.takeItem(this.items, slot);
        if (!removed.isEmpty()) this.changed(slot);
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= this.items.size()) return;
        this.items.set(slot, stack);
        this.changed(slot);
    }

    @Override
    public int getMaxStackSize() {
        return this.layout.maxStackSize();
    }

    @Override
    public void setChanged() {
        if (this.changeListener != null) this.changeListener.run();
    }

    @Override
    public boolean stillValid(Player player) {
        return this.validator.test(player);
    }

    /** Whether automation without a face (a hopper under a plain container) may put the item in. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return this.canInsertFrom(slot, stack, null);
    }

    @Override
    public boolean canTakeItem(Container into, int slot, ItemStack stack) {
        return this.canExtractFrom(slot, null);
    }

    @Override
    public void clearContent() {
        boolean cleared = false;
        for (int slot = 0; slot < this.items.size(); slot++) {
            if (this.items.get(slot).isEmpty()) continue;
            this.items.set(slot, ItemStack.EMPTY);
            cleared = true;
        }
        if (cleared) this.changed(-1);
    }

    // --- Rules ---

    /** Whether the slot's filter takes the item, whoever puts it in. */
    public boolean isItemValid(int slot, ItemStack stack) {
        return this.layout.isItemValid(slot, stack);
    }

    /** Whether a player may put the item in through a menu. */
    public boolean canPlayerPlace(int slot, ItemStack stack) {
        return slot >= 0 && slot < this.items.size() && this.layout.role(slot).playerInsert() && this.isItemValid(slot, stack);
    }

    /** Whether automation may put the item in through a face ({@code null}: any face). */
    public boolean canInsertFrom(int slot, ItemStack stack, @Nullable Direction side) {
        return slot >= 0 && slot < this.items.size() && this.layout.role(slot).automationInsert()
                && this.layout.exposes(slot, side) && this.isItemValid(slot, stack);
    }

    /** Whether automation may take from the slot through a face ({@code null}: any face). */
    public boolean canExtractFrom(int slot, @Nullable Direction side) {
        return slot >= 0 && slot < this.items.size() && this.layout.role(slot).automationExtract() && this.layout.exposes(slot, side);
    }

    /** How many of the item the slot holds. */
    public int getSlotLimit(int slot, ItemStack stack) {
        return Math.min(this.layout.slotLimit(slot), this.getMaxStackSize(stack));
    }

    // --- Moving items ---

    /**
     * Puts as much of {@code stack} as fits into one slot, for the holder's own code: filters and limits apply, roles
     * don't. {@code stack} isn't changed.
     *
     * @return what didn't fit
     */
    public ItemStack insert(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || slot < 0 || slot >= this.items.size() || !this.isItemValid(slot, stack)) return stack;
        ItemStack existing = this.items.get(slot);
        if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, stack)) return stack;
        int space = this.getSlotLimit(slot, stack) - existing.getCount();
        if (space <= 0) return stack;
        int moved = Math.min(space, stack.getCount());
        if (!simulate) {
            this.items.set(slot, stack.copyWithCount(existing.getCount() + moved));
            this.changed(slot);
        }
        return moved == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - moved);
    }

    /** Puts as much of {@code stack} as fits anywhere, topping up matching stacks before filling empty slots. */
    public ItemStack insert(ItemStack stack, boolean simulate) {
        ItemStack remaining = stack;
        for (int pass = 0; pass < 2 && !remaining.isEmpty(); pass++) {
            for (int slot = 0; slot < this.items.size() && !remaining.isEmpty(); slot++) {
                if (this.items.get(slot).isEmpty() == (pass == 0)) continue;
                remaining = this.insert(slot, remaining, simulate);
            }
        }
        return remaining;
    }

    /** Takes up to {@code amount} from one slot, for the holder's own code. */
    public ItemStack extract(int slot, int amount, boolean simulate) {
        ItemStack existing = this.getItem(slot);
        if (existing.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        int taken = Math.min(amount, existing.getCount());
        if (simulate) return existing.copyWithCount(taken);
        return this.removeItem(slot, taken);
    }

    /** Takes up to {@code amount} of the first item matching {@code filter}, gathered from every slot holding it. */
    public ItemStack extract(Predicate<ItemStack> filter, int amount, boolean simulate) {
        ItemStack result = ItemStack.EMPTY;
        for (int slot = 0; slot < this.items.size() && result.getCount() < amount; slot++) {
            ItemStack existing = this.items.get(slot);
            if (existing.isEmpty() || !filter.test(existing)) continue;
            if (!result.isEmpty() && !ItemStack.isSameItemSameComponents(result, existing)) continue;
            ItemStack taken = this.extract(slot, amount - result.getCount(), simulate);
            result = result.isEmpty() ? taken : result.copyWithCount(result.getCount() + taken.getCount());
        }
        return result;
    }

    public int count(Predicate<ItemStack> filter) {
        int count = 0;
        for (ItemStack stack : this.items) {
            if (!stack.isEmpty() && filter.test(stack)) count += stack.getCount();
        }
        return count;
    }

    // --- Contents ---

    /** The slots, in order. Read-only; don't change the stacks in it either. */
    public List<ItemStack> getItems() {
        return Collections.unmodifiableList(this.items);
    }

    /** Copies of the stacks, in order. */
    public List<ItemStack> copyItems() {
        return this.items.stream().map(ItemStack::copy).toList();
    }

    /** Replaces the contents; slots past {@code stacks}' end are emptied. */
    public void setItems(List<ItemStack> stacks) {
        for (int slot = 0; slot < this.items.size(); slot++) this.items.set(slot, slot < stacks.size() ? stacks.get(slot) : ItemStack.EMPTY);
        this.changed(-1);
    }

    /** The contents as an item component (the first {@value ContainerLayout#MAX_ITEM_SLOTS} slots). */
    public ItemContainerContents toContents() {
        return ItemContainerContents.fromItems(this.items.size() > ContainerLayout.MAX_ITEM_SLOTS
                ? this.items.subList(0, ContainerLayout.MAX_ITEM_SLOTS) : this.items);
    }

    public void fromContents(ItemContainerContents contents) {
        this.loadContents(contents);
        this.changed(-1);
    }

    void loadContents(ItemContainerContents contents) {
        NonNullList<ItemStack> stacks = NonNullList.withSize(Math.min(this.items.size(), ContainerLayout.MAX_ITEM_SLOTS), ItemStack.EMPTY);
        contents.copyInto(stacks);
        for (int slot = 0; slot < this.items.size(); slot++) this.items.set(slot, slot < stacks.size() ? stacks.get(slot) : ItemStack.EMPTY);
    }

    // Filling a new container from saved or synced data, without telling anyone.
    void load(int slot, ItemStack stack) {
        this.items.set(slot, stack);
    }

    /** Spills the contents at a position and empties the container. */
    public void dropContents(Level level, BlockPos pos) {
        // Dropping splits the stacks in place, leaving them empty but unannounced.
        Containers.dropContents(level, pos, this);
        for (int slot = 0; slot < this.items.size(); slot++) this.items.set(slot, ItemStack.EMPTY);
        this.changed(-1);
    }

    /** The comparator signal the contents give (0 to 15). */
    public int comparatorSignal() {
        return AbstractContainerMenu.getRedstoneSignalFromContainer(this);
    }

    /** A menu slot that respects the layout's filters, limits and roles. */
    public Slot createSlot(int slot, int x, int y) {
        return new ContainerSlot(this, slot, x, y);
    }

    // --- Listening ---

    /** Calls {@code listener} after a slot changes (slot {@code -1}: several at once). */
    public SlotContainer addListener(Listener listener) {
        this.listeners.add(listener);
        return this;
    }

    public void removeListener(Listener listener) {
        this.listeners.remove(listener);
    }

    /** Who may keep a menu on this container open; everyone by default. */
    public SlotContainer setValidator(Predicate<Player> validator) {
        this.validator = validator;
        return this;
    }

    @Override
    public void setChangeListener(@Nullable Runnable listener) {
        this.changeListener = listener;
    }

    /** Called after a slot changes (slot {@code -1}: several at once). */
    protected void onChanged(int slot) {
    }

    private void changed(int slot) {
        this.onChanged(slot);
        for (Listener listener : this.listeners) listener.onSlotChanged(this, slot);
        this.setChanged();
    }

    @FunctionalInterface
    public interface Listener {
        void onSlotChanged(SlotContainer container, int slot);
    }
}
