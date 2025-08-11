package net.ixdarklord.coolcatlib.api.util;

import com.google.common.collect.Lists;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public class InventoryHelper {
    public static boolean hasContainerMatchingItem(Container container, Item item) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty() && stack.is(item)) return true;
        }
        return false;
    }

    public static int findSlotMatchingItem(Container container, Item item) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty() && stack.is(item)) return i;
        }
        return -1;
    }

    public static List<ItemStack> listMatchingItem(Container container, Item item) {
        return listMatchingItem(container, stack -> stack.is(item));
    }

    public static List<ItemStack> listMatchingItem(Container container, Predicate<ItemStack> predicate) {
        return listMatchingItemSlot(container, predicate).stream().map(SlotReference::getItem).toList();
    }

    public static List<SlotReference.Container> listMatchingItemSlot(Container container, Item item) {
        return listMatchingItemSlot(container, stack -> stack.is(item));
    }

    public static List<SlotReference.Container> listMatchingItemSlot(Container container, Predicate<ItemStack> predicate) {
        List<SlotReference.Container> list = Lists.newArrayList();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (predicate.test(stack)) list.add(new SlotReference.Container(container, i));
        }
        return Collections.unmodifiableList(list);
    }
}
