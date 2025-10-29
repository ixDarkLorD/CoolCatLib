package net.ixdarklord.coolcatlib.api.utils;

import net.minecraft.world.item.ItemStack;

public final class ItemStackHelper {
    public static int hashItemAndTags(ItemStack itemStack) {
        return (31 * (31 + itemStack.getItem().hashCode()) + itemStack.getOrCreateTag().hashCode());
    }
}
