package net.ixdarklord.coolcatcore.api.container;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jetbrains.annotations.Nullable;

/**
 * Inventories kept in an item stack's {@code minecraft:container} component (as shulker boxes keep theirs).
 * <pre>{@code
 * SlotContainer pouch = ItemContainers.of(stack, POUCH_LAYOUT);
 * pouch.insert(new ItemStack(Items.DIAMOND), false);   // written back to the stack right away
 * player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new PouchMenu(id, inventory, pouch), title));
 * }</pre>
 */
public final class ItemContainers {
    private ItemContainers() {}

    /**
     * The stack's inventory: reads the stack now and writes every change back to it. Keep it only as long as the
     * stack is the one in use (a menu should check the player still holds it).
     */
    public static SlotContainer of(ItemStack stack, ContainerLayout layout) {
        if (layout.size() > ContainerLayout.MAX_ITEM_SLOTS) {
            throw new IllegalArgumentException("Only containers of up to " + ContainerLayout.MAX_ITEM_SLOTS + " slots fit in an item");
        }
        SlotContainer container = layout.create();
        container.loadContents(stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY));
        container.setChangeListener(() -> write(stack, container));
        container.setValidator(player -> !stack.isEmpty());
        return container;
    }

    /** The inventory of a {@link ContainerItem}'s stack, or {@code null} for other items. */
    public static @Nullable SlotContainer of(ItemStack stack) {
        return stack.getItem() instanceof ContainerItem item ? of(stack, item.containerLayout(stack)) : null;
    }

    private static void write(ItemStack stack, SlotContainer container) {
        if (stack.isEmpty()) return;
        // An emptied inventory leaves no trace, so the stack still stacks with fresh ones.
        if (container.isEmpty() && !stack.getPrototype().has(DataComponents.CONTAINER)) stack.remove(DataComponents.CONTAINER);
        else stack.set(DataComponents.CONTAINER, container.toContents());
    }
}
