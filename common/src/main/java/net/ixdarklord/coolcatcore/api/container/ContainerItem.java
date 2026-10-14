package net.ixdarklord.coolcatcore.api.container;

import net.minecraft.world.item.ItemStack;

/**
 * An item whose stacks carry an inventory (a backpack, a pouch), kept in the stack's NBT (see
 * {@link ItemContainers}). {@link ItemContainers#of(ItemStack)} and {@code HandlerTypes.CONTAINER.find(stack)} open it.
 * <pre>{@code
 * public class PouchItem extends Item implements ContainerItem {
 *     private static final ContainerLayout LAYOUT = ContainerLayout.builder(9)
 *             .filter(0, 9, stack -> !(stack.getItem() instanceof PouchItem)).build();
 *
 *     public ContainerLayout containerLayout(ItemStack stack) { return LAYOUT; }
 * }
 * }</pre>
 */
public interface ContainerItem {
    /** The stack's inventory layout, of at most {@value ContainerLayout#MAX_ITEM_SLOTS} slots. */
    ContainerLayout containerLayout(ItemStack stack);
}
