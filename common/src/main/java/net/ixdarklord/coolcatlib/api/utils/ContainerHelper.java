package net.ixdarklord.coolcatlib.api.utils;

import com.google.common.collect.Lists;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Utility class providing convenience methods for working with {@link Container} and {@link Player} inventories.
 * Includes item search, filtering, equality, and hashing utilities that simplify inventory-related logic.
 */
public final class ContainerHelper {

    private ContainerHelper() {}

    public static boolean containsItem(Container container, Item item) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty() && stack.is(item)) return true;
        }
        return false;
    }

    public static int findFirstSlotWithItem(Container container, Item item) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty() && stack.is(item)) return i;
        }
        return -1;
    }

    public static List<ItemStack> getAllMatchingStacks(Container container, Item item) {
        return getAllMatchingStacks(container, stack -> stack.is(item));
    }

    public static List<ItemStack> getAllMatchingStacks(Container container, Predicate<ItemStack> predicate) {
        return getAllMatchingSlots(container, predicate).stream().map(SlotReference.Container::get).toList();
    }

    public static List<SlotReference.Container> getAllMatchingSlots(Container container, Item item) {
        return getAllMatchingSlots(container, stack -> stack.is(item));
    }

    public static List<SlotReference.Container> getAllMatchingSlots(Container container, Predicate<ItemStack> predicate) {
        List<SlotReference.Container> slots = Lists.newArrayList();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (predicate.test(stack)) slots.add(new SlotReference.Container(container, i));
        }
        return Collections.unmodifiableList(slots);
    }

    public static NonNullList<ItemStack> getItems(Container container) {
        NonNullList<ItemStack> items = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < items.size(); i++) {
            items.set(i, container.getItem(i));
        }
        return items;
    }

    public static boolean equals(Container a, Container b) {
        if (a.getContainerSize() != b.getContainerSize()) return false;
        for (int i = 0; i < a.getContainerSize(); i++) {
            if (!ItemStack.isSameItemSameTags(a.getItem(i), b.getItem(i))) return false;
        }
        return true;
    }

    public static int hashCode(Container container) {
        int code = 31 + container.getContainerSize();
        for (int i = 0; i < container.getContainerSize(); i++) {
            code += (ItemStackHelper.hashItemAndTags(container.getItem(i)) * 2);
        }
        return code;
    }

    public static boolean containsItem(Player player, Item item) {
        return findFirstSlotWithItem(player, item) != -1;
    }

    public static int findFirstSlotWithItem(Player player, Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(item)) return i;
        }
        return -1;
    }

    public static List<ItemStack> getAllMatchingStacks(Player player, Item item) {
        return getAllMatchingStacks(player, stack -> stack.is(item));
    }

    public static List<ItemStack> getAllMatchingStacks(Player player, Predicate<ItemStack> predicate) {
        return getAllMatchingSlots(player, predicate).stream().map(SlotReference.Player::get).toList();
    }

    public static List<SlotReference.Player> getAllMatchingSlots(Player player, Item item) {
        return getAllMatchingSlots(player, stack -> stack.is(item));
    }

    public static List<SlotReference.Player> getAllMatchingSlots(Player player, Predicate<ItemStack> predicate) {
        List<SlotReference.Player> slots = Lists.newArrayList();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (predicate.test(stack)) slots.add(new SlotReference.Player(player, i));
        }
        return Collections.unmodifiableList(slots);
    }

    public static NonNullList<ItemStack> getItems(Player player) {
        NonNullList<ItemStack> items = NonNullList.withSize(player.getInventory().getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < items.size(); i++) {
            items.set(i, player.getInventory().getItem(i));
        }
        return items;
    }

    public static boolean equals(Player a, Player b) {
        if (a.getInventory().getContainerSize() != b.getInventory().getContainerSize()) return false;
        for (int i = 0; i < a.getInventory().getContainerSize(); i++) {
            if (!ItemStack.isSameItemSameTags(a.getInventory().getItem(i), b.getInventory().getItem(i))) return false;
        }
        return true;
    }

    public static int hashCode(Player player) {
        int code = 31 + player.getInventory().getContainerSize();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            code += (ItemStackHelper.hashItemAndTags(player.getInventory().getItem(i)) * 2);
        }
        return code;
    }
}
