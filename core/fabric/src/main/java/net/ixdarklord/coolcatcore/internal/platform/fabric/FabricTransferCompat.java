package net.ixdarklord.coolcatcore.internal.platform.fabric;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedSlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.ixdarklord.coolcatcore.api.container.ContainerItem;
import net.ixdarklord.coolcatcore.api.container.ContainerLayout;
import net.ixdarklord.coolcatcore.api.container.SidedContainerView;
import net.ixdarklord.coolcatcore.api.handler.HandlerTypes;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// Exposes CoolCatLib: Core containers to Fabric's Transfer API, so other mods' pipes and tools reach them. Block entities
// that are containers (ExtendedContainerBlockEntity included) are already covered by Fabric's own container fallback;
// this adds containers that blocks hand out through HandlerTypes.CONTAINER, and ContainerItem stacks.
public final class FabricTransferCompat {
    private FabricTransferCompat() {}

    public static void register() {
        ItemStorage.SIDED.registerFallback((level, pos, state, blockEntity, side) -> {
            if (blockEntity instanceof Container) return null;
            Container container = HandlerTypes.CONTAINER.find(level, pos, state, blockEntity, side);
            if (container == null) return null;
            if (container instanceof SidedContainerView view) return InventoryStorage.of(view.getContainer(), view.getSide());
            return InventoryStorage.of(container, side);
        });
        ItemStorage.ITEM.registerFallback((stack, context) -> stack.getItem() instanceof ContainerItem item
                ? LayoutItemStorage.create(context, item.containerLayout(stack)) : null);
    }

    // A ContainerItem stack's contents, changed through the item context (so the change reaches wherever the stack
    // is, inside the transaction). Follows the layout: filters and roles decide what goes in and out, limits how much.
    private static final class LayoutItemStorage extends CombinedSlottedStorage<ItemVariant, SingleSlotStorage<ItemVariant>> {
        private LayoutItemStorage(List<SingleSlotStorage<ItemVariant>> slots) {
            super(slots);
        }

        static LayoutItemStorage create(ContainerItemContext context, ContainerLayout layout) {
            Item original = context.getItemVariant().getItem();
            int size = Math.min(layout.size(), ContainerLayout.MAX_ITEM_SLOTS);
            List<SingleSlotStorage<ItemVariant>> slots = new ArrayList<>(size);
            for (int slot = 0; slot < size; slot++) slots.add(new Slot(context, layout, original, slot));
            return new LayoutItemStorage(slots);
        }
    }

    private record Slot(ContainerItemContext context, ContainerLayout layout, Item original, int slot) implements SingleSlotStorage<ItemVariant> {
        private boolean isStillValid() {
            return this.context.getItemVariant().getItem() == this.original;
        }

        private ItemContainerContents contents() {
            return this.context.getItemVariant().getComponentMap().getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        }

        private ItemStack getStack() {
            return this.contents().stream().skip(this.slot).findFirst().orElse(ItemStack.EMPTY);
        }

        private boolean setStack(ItemStack stack, TransactionContext transaction) {
            List<ItemStack> stacks = this.contents().stream().collect(Collectors.toList());
            while (stacks.size() <= this.slot) stacks.add(ItemStack.EMPTY);
            stacks.set(this.slot, stack);
            ItemVariant changed = this.context.getItemVariant().withComponentChanges(DataComponentPatch.builder()
                    .set(DataComponents.CONTAINER, ItemContainerContents.fromItems(stacks))
                    .build());
            return this.context.exchange(changed, 1, transaction) == 1;
        }

        @Override
        public long insert(ItemVariant variant, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(variant, maxAmount);
            if (!this.isStillValid() || !this.layout.role(this.slot).automationInsert()) return 0;
            ItemStack current = this.getStack();
            if (!current.isEmpty() && !variant.matches(current)) return 0;
            ItemStack inserted = variant.toStack();
            if (!this.layout.isItemValid(this.slot, inserted) || !variant.getItem().canFitInsideContainerItems()) return 0;
            int capacity = Math.min(this.layout.slotLimit(this.slot), inserted.getMaxStackSize());
            int amount = (int) Math.min(maxAmount, capacity - current.getCount());
            if (amount <= 0) return 0;
            ItemStack updated = current.isEmpty() ? variant.toStack(amount) : current.copyWithCount(current.getCount() + amount);
            return this.setStack(updated, transaction) ? amount : 0;
        }

        @Override
        public long extract(ItemVariant variant, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(variant, maxAmount);
            if (!this.isStillValid() || !this.layout.role(this.slot).automationExtract()) return 0;
            ItemStack current = this.getStack();
            if (!variant.matches(current)) return 0;
            int amount = (int) Math.min(current.getCount(), maxAmount);
            if (amount <= 0) return 0;
            ItemStack updated = amount == current.getCount() ? ItemStack.EMPTY : current.copyWithCount(current.getCount() - amount);
            return this.setStack(updated, transaction) ? amount : 0;
        }

        @Override
        public boolean isResourceBlank() {
            return this.getStack().isEmpty();
        }

        @Override
        public ItemVariant getResource() {
            return ItemVariant.of(this.getStack());
        }

        @Override
        public long getAmount() {
            return this.getStack().getCount();
        }

        @Override
        public long getCapacity() {
            ItemStack stack = this.getStack();
            return Math.min(this.layout.slotLimit(this.slot), stack.isEmpty() ? Item.ABSOLUTE_MAX_STACK_SIZE : stack.getMaxStackSize());
        }
    }
}
