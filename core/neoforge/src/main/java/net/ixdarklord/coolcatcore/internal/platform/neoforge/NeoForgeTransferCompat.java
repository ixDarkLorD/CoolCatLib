package net.ixdarklord.coolcatcore.internal.platform.neoforge;

import net.ixdarklord.coolcatcore.api.block.ExtendedContainerBlockEntity;
import net.ixdarklord.coolcatcore.api.container.ContainerItem;
import net.ixdarklord.coolcatcore.api.container.ContainerLayout;
import net.ixdarklord.coolcatcore.api.container.SidedContainerView;
import net.ixdarklord.coolcatcore.api.handler.HandlerTypes;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.ComponentItemHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import org.jetbrains.annotations.Nullable;

// Exposes CoolCatLib: Core containers as NeoForge item handler capabilities, so other mods' pipes and tools reach them:
// blocks and entities through HandlerTypes.CONTAINER, ContainerItem stacks through their minecraft:container
// component. Vanilla containers are left to NeoForge's own providers.
public final class NeoForgeTransferCompat {
    private NeoForgeTransferCompat() {}

    public static void register(RegisterCapabilitiesEvent event) {
        for (BlockEntityType<?> type : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, NeoForgeTransferCompat::blockHandler);
        }
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            event.registerEntity(Capabilities.ItemHandler.ENTITY, type, (entity, context) -> entityHandler(entity, null));
            event.registerEntity(Capabilities.ItemHandler.ENTITY_AUTOMATION, type, NeoForgeTransferCompat::entityHandler);
        }
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof ContainerItem containerItem) {
                event.registerItem(Capabilities.ItemHandler.ITEM, (stack, context) -> new LayoutItemHandler(stack, containerItem.containerLayout(stack)), item);
            }
        }
    }

    private static @Nullable IItemHandler blockHandler(BlockEntity blockEntity, @Nullable Direction side) {
        Level level = blockEntity.getLevel();
        if (level == null || (blockEntity instanceof Container && !(blockEntity instanceof ExtendedContainerBlockEntity))) return null;
        Container container = HandlerTypes.CONTAINER.find(level, blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, side);
        return wrap(container, side);
    }

    private static @Nullable IItemHandler entityHandler(Entity entity, @Nullable Direction side) {
        if (entity instanceof Container) return null;
        return wrap(HandlerTypes.CONTAINER.find(entity, side), side);
    }

    private static @Nullable IItemHandler wrap(@Nullable Container container, @Nullable Direction side) {
        if (container == null) return null;
        // NeoForge's sided wrapper applies the face rules itself.
        if (container instanceof SidedContainerView view) return new SidedInvWrapper(view.getContainer(), view.getSide());
        if (container instanceof WorldlyContainer worldly) return new SidedInvWrapper(worldly, side);
        return new InvWrapper(container);
    }

    // A ContainerItem stack's minecraft:container contents, changed on the stack itself. Follows the layout: filters and
    // roles decide what goes in and out, limits how much.
    private static final class LayoutItemHandler extends ComponentItemHandler {
        private final ContainerLayout layout;

        LayoutItemHandler(ItemStack stack, ContainerLayout layout) {
            super(stack, DataComponents.CONTAINER, Math.min(layout.size(), ContainerLayout.MAX_ITEM_SLOTS));
            this.layout = layout;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return super.isItemValid(slot, stack) && this.layout.role(slot).automationInsert()
                    && this.layout.isItemValid(slot, stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return Math.min(super.getSlotLimit(slot), this.layout.slotLimit(slot));
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!this.layout.role(slot).automationExtract()) return ItemStack.EMPTY;
            return super.extractItem(slot, amount, simulate);
        }
    }
}
