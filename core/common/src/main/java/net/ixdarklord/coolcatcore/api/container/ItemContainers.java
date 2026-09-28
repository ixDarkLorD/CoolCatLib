package net.ixdarklord.coolcatcore.api.container;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Inventories kept in an item stack's NBT, as vanilla keeps them: an {@code Items} list in the stack's
 * {@code BlockEntityTag} for block items (as shulker boxes keep theirs, so placing the block restores the contents), and
 * in the stack's own tag for other items (as bundles do).
 * <pre>{@code
 * SlotContainer pouch = ItemContainers.of(stack, POUCH_LAYOUT);
 * pouch.insert(new ItemStack(Items.DIAMOND), false);   // written back to the stack right away
 * player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new PouchMenu(id, inventory, pouch), title));
 * }</pre>
 */
public final class ItemContainers {
    /** The list holding the contents, in {@link net.minecraft.world.ContainerHelper#saveAllItems} format. */
    public static final String ITEMS_TAG = "Items";

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
        CompoundTag contents = contentsTag(stack);
        if (contents != null) container.loadContents(contents);
        container.setChangeListener(() -> write(stack, container));
        container.setValidator(player -> !stack.isEmpty());
        return container;
    }

    /** The inventory of a {@link ContainerItem}'s stack, or {@code null} for other items. */
    public static @Nullable SlotContainer of(ItemStack stack) {
        return stack.getItem() instanceof ContainerItem item ? of(stack, item.containerLayout(stack)) : null;
    }

    /**
     * The tag holding the stack's {@code Items} list: its {@code BlockEntityTag} for block items, its own tag
     * otherwise; {@code null} when there's none.
     */
    public static @Nullable CompoundTag contentsTag(ItemStack stack) {
        return stack.getItem() instanceof BlockItem ? BlockItem.getBlockEntityData(stack) : stack.getTag();
    }

    private static void write(ItemStack stack, SlotContainer container) {
        if (stack.isEmpty()) return;
        boolean block = stack.getItem() instanceof BlockItem;
        if (container.isEmpty()) {
            // An emptied inventory leaves no trace, so the stack still stacks with fresh ones.
            CompoundTag tag = contentsTag(stack);
            if (tag == null || !tag.contains(ITEMS_TAG, Tag.TAG_LIST)) return;
            if (!block) {
                stack.removeTagKey(ITEMS_TAG);
                return;
            }
            tag.remove(ITEMS_TAG);
            if (tag.isEmpty()) stack.removeTagKey(BlockItem.BLOCK_ENTITY_TAG);
            return;
        }
        CompoundTag tag = block ? stack.getOrCreateTagElement(BlockItem.BLOCK_ENTITY_TAG) : stack.getOrCreateTag();
        tag.put(ITEMS_TAG, container.toContents().get(ITEMS_TAG));
    }
}
