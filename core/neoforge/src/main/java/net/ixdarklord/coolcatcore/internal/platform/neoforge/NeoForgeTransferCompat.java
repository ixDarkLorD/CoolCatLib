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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.ItemAccessItemHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

// Exposes CoolCatLib: Core containers as NeoForge item handler capabilities, so other mods' pipes and tools reach them:
// blocks and entities through HandlerTypes.CONTAINER, ContainerItem stacks through their minecraft:container
// component. Vanilla containers are left to NeoForge's own providers.
public final class NeoForgeTransferCompat {
    private NeoForgeTransferCompat() {}

    public static void register(RegisterCapabilitiesEvent event) {
        for (BlockEntityType<?> type : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
            event.registerBlockEntity(Capabilities.Item.BLOCK, type, NeoForgeTransferCompat::blockHandler);
        }
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            event.registerEntity(Capabilities.Item.ENTITY, type, (entity, context) -> entityHandler(entity, null));
            event.registerEntity(Capabilities.Item.ENTITY_AUTOMATION, type, NeoForgeTransferCompat::entityHandler);
        }
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof ContainerItem containerItem) {
                event.registerItem(Capabilities.Item.ITEM, (stack, access) -> new LayoutItemHandler(access, containerItem.containerLayout(stack)), item);
            }
        }
    }

    private static @Nullable ResourceHandler<ItemResource> blockHandler(BlockEntity blockEntity, @Nullable Direction side) {
        Level level = blockEntity.getLevel();
        if (level == null || (blockEntity instanceof Container && !(blockEntity instanceof ExtendedContainerBlockEntity))) return null;
        Container container = HandlerTypes.CONTAINER.find(level, blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, side);
        return wrap(container, side);
    }

    private static @Nullable ResourceHandler<ItemResource> entityHandler(Entity entity, @Nullable Direction side) {
        if (entity instanceof Container) return null;
        return wrap(HandlerTypes.CONTAINER.find(entity, side), side);
    }

    private static @Nullable ResourceHandler<ItemResource> wrap(@Nullable Container container, @Nullable Direction side) {
        if (container == null) return null;
        // NeoForge's worldly wrapper applies the face rules itself.
        if (container instanceof SidedContainerView view) return new WorldlyContainerWrapper(view.getContainer(), view.getSide());
        if (container instanceof WorldlyContainer worldly) return new WorldlyContainerWrapper(worldly, side);
        return VanillaContainerWrapper.of(container);
    }

    // A ContainerItem stack's contents, through the item access so the change reaches wherever the stack is. Follows
    // the layout: filters and roles decide what goes in and out, limits how much.
    private static final class LayoutItemHandler extends ItemAccessItemHandler {
        private final ContainerLayout layout;

        LayoutItemHandler(ItemAccess access, ContainerLayout layout) {
            super(access, DataComponents.CONTAINER, Math.min(layout.size(), ContainerLayout.MAX_ITEM_SLOTS));
            this.layout = layout;
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return super.isValid(index, resource) && this.layout.role(index).automationInsert()
                    && this.layout.isItemValid(index, resource.toStack());
        }

        @Override
        protected int getCapacity(int index, ItemResource resource) {
            return Math.min(super.getCapacity(index, resource), this.layout.slotLimit(index));
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            if (!this.layout.role(index).automationExtract()) return 0;
            return super.extract(index, resource, amount, transaction);
        }
    }
}
