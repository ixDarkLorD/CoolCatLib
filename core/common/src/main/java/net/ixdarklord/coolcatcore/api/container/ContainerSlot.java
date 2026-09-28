package net.ixdarklord.coolcatcore.api.container;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A menu slot of a {@link SlotContainer} that follows its layout: filters and {@link SlotRole#OUTPUT} decide what a
 * player may put in, and slot limits how much.
 */
public class ContainerSlot extends Slot {
    private final SlotContainer holder;
    private final int slot;

    public ContainerSlot(SlotContainer container, int slot, int x, int y) {
        super(container, slot, x, y);
        this.holder = container;
        this.slot = slot;
    }

    public SlotContainer getHolder() {
        return this.holder;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return this.holder.canPlayerPlace(this.slot, stack);
    }

    @Override
    public int getMaxStackSize() {
        return this.holder.layout().slotLimit(this.slot);
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return this.holder.getSlotLimit(this.slot, stack);
    }
}
