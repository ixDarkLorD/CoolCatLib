package net.ixdarklord.coolcatlib.api.util;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a reference to a specific slot within an inventory or container.
 * A slot reference provides access to the item stored in that slot and allows modification.
 * It is associated with an entity or block entity that manages an inventory.
 *
 * @param <T> The type of the owner managing the inventory (e.g., a player or a block entity).
 */
public abstract class SlotReference<T> {

    /**
     * Represents an empty or invalid slot. Used as a placeholder when no valid slot is available.
     * <p>
     * This instance always returns {@link ItemStack#EMPTY} and does not allow modifications.
     * </p>
     */
    public static final SlotReference<?> EMPTY = new SlotReference<>(null, -1) {
        @Override
        public @NotNull ItemStack getItem() {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean setItem(ItemStack item) {
            return false; // Modification is not allowed
        }

        @Override
        public boolean removeItem() {
            return false; // Removal is not allowed
        }
    };

    /**
     * The owner managing the inventory that contains this slot.
     * This could be a player, a block entity, or another object responsible for inventory management.
     * It may be {@code null}, indicating an invalid or uninitialized slot.
     */
    protected final T owner;

    /**
     * The index of this slot within the inventory or container.
     */
    protected final int index;

    /**
     * Constructs a new {@code SlotReference} with the specified owner and index.
     *
     * @param owner The entity or block entity managing the inventory containing this slot, or {@code null} if invalid.
     * @param index The index of this slot within the inventory.
     */
    public SlotReference(@Nullable T owner, int index) {
        this.owner = owner;
        this.index = index;
    }

    /**
     * Returns the owner managing the inventory that contains this slot.
     *
     * @return The owner, or {@code null} if the slot is invalid.
     */
    @Nullable
    public T getOwner() {
        return owner;
    }

    /**
     * Returns the index of this slot within the container.
     *
     * @return The slot index.
     */
    public int getIndex() {
        return index;
    }

    /**
     * Retrieves the item currently stored in this slot.
     *
     * @return The stored {@link ItemStack}, or {@link ItemStack#EMPTY} if the slot is empty.
     * @throws IllegalStateException if the owner is {@code null}.
     */
    @NotNull
    public abstract ItemStack getItem();

    /**
     * Sets the item in this slot.
     *
     * @param item The {@link ItemStack} to be placed in the slot.
     * @return {@code true} if the item was successfully set, {@code false} otherwise.
     * @throws IllegalStateException if the owner is {@code null}.
     */
    public abstract boolean setItem(ItemStack item);

    /**
     * Removes the item from this slot by setting it to an empty stack.
     * This is equivalent to calling {@code setItem(ItemStack.EMPTY)}.
     *
     * @return {@code true} if the item was successfully removed, {@code false} if it was already empty.
     * @throws IllegalStateException if the owner is {@code null}.
     */
    public boolean removeItem() {
        if (this.getItem().isEmpty()) {
            return false;
        }
        return this.setItem(ItemStack.EMPTY);
    }

    /**
     * Represents a player's inventory slot, allowing access to a specific slot in a player's inventory.
     */
    public static class Player extends SlotReference<net.minecraft.world.entity.player.Player> {

        /**
         * Constructs a new {@code Player} slot reference with the specified player and index.
         *
         * @param player The player who owns the inventory containing this slot.
         * @param index  The index of the slot in the player's inventory.
         */
        public Player(@NotNull net.minecraft.world.entity.player.Player player, int index) {
            super(player, index);
        }

        @Override
        public @NotNull ItemStack getItem() {
            if (owner == null) {
                throw new IllegalStateException("Cannot retrieve item: Slot owner is null.");
            }
            return owner.getSlot(index).get();
        }

        @Override
        public boolean setItem(ItemStack stack) {
            if (owner == null) {
                throw new IllegalStateException("Cannot set item: Slot owner is null.");
            }
            if (ItemStack.matches(this.getItem(), stack)) {
                return false; // Prevent redundant item setting
            }
            owner.getSlot(this.index).set(stack);
            return true;
        }
    }

    /**
     * Represents a slot in a generic container, such as a chest or furnace.
     */
    public static class Container extends SlotReference<net.minecraft.world.Container> {

        /**
         * Constructs a new {@code ContainerSlot} with the specified container and index.
         *
         * @param container The container that holds this slot.
         * @param index     The index of the slot in the container.
         */
        public Container(@Nullable net.minecraft.world.Container container, int index) {
            super(container, index);
        }

        @Override
        public @NotNull ItemStack getItem() {
            if (owner == null) {
                throw new IllegalStateException("Cannot retrieve item: Slot owner is null.");
            }
            return owner.getItem(index);
        }

        @Override
        public boolean setItem(ItemStack stack) {
            if (owner == null) {
                throw new IllegalStateException("Cannot set item: Slot owner is null.");
            }
            if (ItemStack.matches(this.getItem(), stack)) {
                return false; // Prevent redundant item setting
            }
            owner.setItem(this.index, stack);
            return true;
        }
    }
}
