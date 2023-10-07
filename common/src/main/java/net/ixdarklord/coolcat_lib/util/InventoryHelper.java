package net.ixdarklord.coolcat_lib.util;

import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

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
}
