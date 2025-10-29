package net.ixdarklord.coolcatlib.api.utils;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a reference to a specific slot within an inventory or container.
 * <p>
 * A {@code SlotReference} provides direct access to an {@link ItemStack} stored in a slot,
 * allowing retrieval, modification, and clearing operations.
 * </p>
 *
 * @param <T> The type of the inventory owner (e.g., {@link net.minecraft.world.entity.player.Player} or {@link net.minecraft.world.Container}).
 */
public abstract class SlotReference<T> {

    /**
     * A static empty slot reference used as a placeholder for invalid or unavailable slots.
     * <p>
     * Always returns {@link ItemStack#EMPTY} and does not permit modifications.
     * </p>
     */
    public static final SlotReference<?> EMPTY = new SlotReference<>(null, -1) {
        @Override
        public @NotNull ItemStack get() {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean set(ItemStack item) {
            return false;
        }

        @Override
        public boolean clear() {
            return false;
        }
    };

    protected final T owner;
    protected final int index;

    /**
     * Creates a new {@code SlotReference} instance.
     *
     * @param owner the inventory owner managing this slot, or {@code null} if invalid
     * @param index the index of the slot within the inventory
     */
    public SlotReference(@Nullable T owner, int index) {
        this.owner = owner;
        this.index = index;
    }

    /**
     * Returns the inventory owner associated with this slot.
     *
     * @return the owner, or {@code null} if this reference is invalid
     */
    @Nullable
    public T getOwner() {
        return owner;
    }

    /**
     * Returns the slot index within the container or inventory.
     *
     * @return the slot index
     */
    public int getIndex() {
        return index;
    }

    /**
     * Retrieves the {@link ItemStack} currently stored in this slot.
     *
     * @return the stored item, or {@link ItemStack#EMPTY} if the slot is empty
     * @throws IllegalStateException if the owner is {@code null}
     */
    public abstract @NotNull ItemStack get();

    /**
     * Replaces the item in this slot.
     *
     * @param stack the new {@link ItemStack} to place
     * @return {@code true} if the item was successfully set, {@code false} if unchanged
     * @throws IllegalStateException if the owner is {@code null}
     */
    public abstract boolean set(ItemStack stack);

    /**
     * Clears the slot by setting its contents to {@link ItemStack#EMPTY}.
     *
     * @return {@code true} if an item was removed, {@code false} if already empty
     * @throws IllegalStateException if the owner is {@code null}
     */
    public boolean clear() {
        ItemStack current = get();
        if (current.isEmpty()) {
            return false;
        }
        return set(ItemStack.EMPTY);
    }

    /**
     * A {@code SlotReference} implementation for player inventories.
     */
    public static class Player extends SlotReference<net.minecraft.world.entity.player.Player> {

        /**
         * Creates a new player inventory slot reference.
         *
         * @param player the player owning the inventory
         * @param index  the slot index within the player's inventory
         */
        public Player(@NotNull net.minecraft.world.entity.player.Player player, int index) {
            super(player, index);
        }

        @Override
        public @NotNull ItemStack get() {
            if (owner == null) {
                throw new IllegalStateException("Cannot retrieve item: owner is null.");
            }
            return owner.getSlot(index).get();
        }

        @Override
        public boolean set(ItemStack stack) {
            if (owner == null) {
                throw new IllegalStateException("Cannot set item: owner is null.");
            }
            if (ItemStack.matches(get(), stack)) {
                return false;
            }
            owner.getSlot(index).set(stack);
            return true;
        }
    }

    /**
     * A {@code SlotReference} implementation for generic containers (e.g., chests, furnaces).
     */
    public static class Container extends SlotReference<net.minecraft.world.Container> {

        /**
         * Creates a new container slot reference.
         *
         * @param container the container owning this slot
         * @param index     the slot index within the container
         */
        public Container(@Nullable net.minecraft.world.Container container, int index) {
            super(container, index);
        }

        @Override
        public @NotNull ItemStack get() {
            if (owner == null) {
                throw new IllegalStateException("Cannot retrieve item: owner is null.");
            }
            return owner.getItem(index);
        }

        @Override
        public boolean set(ItemStack stack) {
            if (owner == null) {
                throw new IllegalStateException("Cannot set item: owner is null.");
            }
            if (ItemStack.matches(get(), stack)) {
                return false;
            }
            owner.setItem(index, stack);
            return true;
        }
    }
}
