package net.ixdarklord.coolcatcore.api.handler;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * An item that hands out handlers for its stacks; {@link HandlerType#find(ItemStack)} asks it before any registered
 * provider.
 */
public interface ItemHandlerProvider {
    <T> @Nullable T getHandler(HandlerType<T> type, ItemStack stack);
}
