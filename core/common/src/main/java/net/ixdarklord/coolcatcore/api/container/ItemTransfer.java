package net.ixdarklord.coolcatcore.api.container;

import net.ixdarklord.coolcatcore.api.block.ExtendedContainerBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Moving items in and out of any {@link Container} the way automation does: through a face ({@code null} for none),
 * following {@link WorldlyContainer} face rules, {@link Container#canPlaceItem}/{@link Container#canTakeItem} and slot
 * limits. Works with vanilla containers, {@link SlotContainer}s and anything a
 * {@link net.ixdarklord.coolcatcore.api.handler.HandlerTypes#CONTAINER} lookup returns.
 * <pre>{@code
 * Container below = HandlerTypes.CONTAINER.find(level, pos.below(), Direction.UP);
 * if (below != null) ItemTransfer.move(this, Direction.DOWN, below, Direction.UP, stack -> true, 8);
 * }</pre>
 */
public final class ItemTransfer {
    private ItemTransfer() {}

    /** Puts as much of {@code stack} as fits into the container. {@code stack} isn't changed. */
    public static ItemStack insert(Container target, @Nullable Direction side, ItemStack stack, boolean simulate) {
        Container view = view(target, side);
        ItemStack remaining = stack;
        boolean changed = false;
        // Top up matching stacks first, then fill empty slots.
        for (int pass = 0; pass < 2 && !remaining.isEmpty(); pass++) {
            for (int slot = 0; slot < view.getContainerSize() && !remaining.isEmpty(); slot++) {
                ItemStack existing = view.getItem(slot);
                if (existing.isEmpty() == (pass == 0) || !view.canPlaceItem(slot, remaining)) continue;
                if (!existing.isEmpty() && !ItemStack.isSameItemSameTags(existing, remaining)) continue;
                int space = slotLimit(view, slot, remaining) - existing.getCount();
                if (space <= 0) continue;
                int moved = Math.min(space, remaining.getCount());
                if (!simulate) {
                    view.setItem(slot, remaining.copyWithCount(existing.getCount() + moved));
                    changed = true;
                }
                remaining = moved == remaining.getCount() ? ItemStack.EMPTY : remaining.copyWithCount(remaining.getCount() - moved);
            }
        }
        if (changed) view.setChanged();
        return remaining;
    }

    public static ItemStack insert(Container target, ItemStack stack, boolean simulate) {
        return insert(target, null, stack, simulate);
    }

    /**
     * Takes up to {@code amount} of the first item matching {@code filter} that may be taken, gathered from every
     * slot holding it.
     */
    public static ItemStack extract(Container source, @Nullable Direction side, Predicate<ItemStack> filter, int amount, boolean simulate) {
        Container view = view(source, side);
        ItemStack result = ItemStack.EMPTY;
        for (int slot = 0; slot < view.getContainerSize() && result.getCount() < amount; slot++) {
            ItemStack existing = view.getItem(slot);
            if (existing.isEmpty() || !filter.test(existing) || !view.canTakeItem(source, slot, existing)) continue;
            if (!result.isEmpty() && !ItemStack.isSameItemSameTags(result, existing)) continue;
            int count = Math.min(amount - result.getCount(), existing.getCount());
            ItemStack taken = simulate ? existing.copyWithCount(count) : view.removeItem(slot, count);
            if (taken.isEmpty()) continue;
            result = result.isEmpty() ? taken : result.copyWithCount(result.getCount() + taken.getCount());
        }
        if (!simulate && !result.isEmpty()) view.setChanged();
        return result;
    }

    public static ItemStack extract(Container source, Predicate<ItemStack> filter, int amount, boolean simulate) {
        return extract(source, null, filter, amount, simulate);
    }

    /**
     * Moves up to {@code amount} items matching {@code filter} from one container to another, only as many as the
     * target takes.
     *
     * @return how many moved
     */
    public static int move(Container from, @Nullable Direction fromSide, Container to, @Nullable Direction toSide, Predicate<ItemStack> filter, int amount) {
        Container source = view(from, fromSide);
        int moved = 0;
        for (int slot = 0; slot < source.getContainerSize() && moved < amount; slot++) {
            ItemStack existing = source.getItem(slot);
            if (existing.isEmpty() || !filter.test(existing) || !source.canTakeItem(to, slot, existing)) continue;
            ItemStack attempt = existing.copyWithCount(Math.min(amount - moved, existing.getCount()));
            int fits = attempt.getCount() - insert(to, toSide, attempt, true).getCount();
            if (fits <= 0) continue;
            ItemStack taken = source.removeItem(slot, fits);
            ItemStack rest = insert(to, toSide, taken, false);
            // The target changed its mind between simulating and inserting: put the rest back.
            if (!rest.isEmpty()) {
                ItemStack current = source.getItem(slot);
                source.setItem(slot, current.isEmpty() ? rest : current.copyWithCount(current.getCount() + rest.getCount()));
            }
            moved += taken.getCount() - rest.getCount();
        }
        if (moved > 0) source.setChanged();
        return moved;
    }

    public static int move(Container from, Container to, int amount) {
        return move(from, null, to, null, stack -> true, amount);
    }

    /** How many of the item a container's slot holds, with per-slot limits where the container has them. */
    public static int slotLimit(Container container, int slot, ItemStack stack) {
        if (container instanceof SidedContainerView view) return slotLimit(view.getContainer(), view.getContainerSlot(slot), stack);
        if (container instanceof SlotContainer holder) return holder.getSlotLimit(slot, stack);
        if (container instanceof ExtendedContainerBlockEntity blockEntity) return blockEntity.getContainer().getSlotLimit(slot, stack);
        return Math.min(container.getMaxStackSize(), stack.getMaxStackSize());
    }

    private static Container view(Container container, @Nullable Direction side) {
        return side != null && container instanceof WorldlyContainer worldly && !(container instanceof SidedContainerView)
                ? new SidedContainerView(worldly, side) : container;
    }
}
