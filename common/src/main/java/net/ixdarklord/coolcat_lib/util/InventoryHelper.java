package net.ixdarklord.coolcat_lib.util;

import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

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
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty() && stack.is(item)) result.add(stack);
        }
        return result;
    }
}
