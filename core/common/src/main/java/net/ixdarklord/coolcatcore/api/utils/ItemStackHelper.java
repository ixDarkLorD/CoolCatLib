package net.ixdarklord.coolcatcore.api.utils;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Item stack helpers for what 1.20.1's {@link ItemStack} lacks (it has NBT tags, not data components). */
public final class ItemStackHelper {
    private ItemStackHelper() {}

    /**
     * A hash of the stack's item and tag, ignoring the count: equal for stacks that
     * {@link ItemStack#isSameItemSameTags} considers the same. The 1.20.1 counterpart of newer versions'
     * {@code ItemStack.hashItemAndComponents}.
     */
    public static int hashItemAndTags(@Nullable ItemStack stack) {
        if (stack == null) return 0;
        return 31 * (31 + stack.getItem().hashCode()) + Objects.hashCode(stack.getTag());
    }
}
