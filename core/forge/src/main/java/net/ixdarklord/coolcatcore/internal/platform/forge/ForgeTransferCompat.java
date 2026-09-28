package net.ixdarklord.coolcatcore.internal.platform.forge;

import net.ixdarklord.coolcatcore.api.block.ExtendedContainerBlockEntity;
import net.ixdarklord.coolcatcore.api.container.ContainerItem;
import net.ixdarklord.coolcatcore.api.container.ItemContainers;
import net.ixdarklord.coolcatcore.api.container.ItemTransfer;
import net.ixdarklord.coolcatcore.api.container.SidedContainerView;
import net.ixdarklord.coolcatcore.api.handler.HandlerTypes;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

// Exposes CoolCatLib: Core containers as Forge item handler capabilities, so other mods' pipes and tools reach them:
// blocks and entities through HandlerTypes.CONTAINER, ContainerItem stacks through the inventory in their NBT. Vanilla
// containers are left to Forge's own capabilities. The handlers follow the same rules as ItemTransfer: faces, roles,
// filters and slot limits.
public final class ForgeTransferCompat {
    private static final ResourceLocation KEY = CoolCatCore.rl("container");
    // The providers attached to block entities, for invalidating them by position.
    private static final Map<BlockEntity, HandlerProvider> BLOCK_PROVIDERS = Collections.synchronizedMap(new WeakHashMap<>());

    private ForgeTransferCompat() {}

    /** Registers the capability listeners on Forge's event bus ({@code MinecraftForge.EVENT_BUS}). */
    public static void register(IEventBus forgeBus) {
        forgeBus.addGenericListener(BlockEntity.class, (AttachCapabilitiesEvent<BlockEntity> event) -> {
            BlockEntity blockEntity = event.getObject();
            if (blockEntity instanceof Container && !(blockEntity instanceof ExtendedContainerBlockEntity)) return;
            HandlerProvider provider = new HandlerProvider(side -> {
                Level level = blockEntity.getLevel();
                if (level == null) return null;
                return HandlerTypes.CONTAINER.find(level, blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, side);
            });
            event.addCapability(KEY, provider);
            event.addListener(provider::invalidate);
            BLOCK_PROVIDERS.put(blockEntity, provider);
        });
        forgeBus.addGenericListener(Entity.class, (AttachCapabilitiesEvent<Entity> event) -> {
            Entity entity = event.getObject();
            if (entity instanceof Container) return;
            HandlerProvider provider = new HandlerProvider(side -> HandlerTypes.CONTAINER.find(entity, side));
            event.addCapability(KEY, provider);
            event.addListener(provider::invalidate);
        });
        forgeBus.addGenericListener(ItemStack.class, (AttachCapabilitiesEvent<ItemStack> event) -> {
            ItemStack stack = event.getObject();
            if (!(stack.getItem() instanceof ContainerItem item)) return;
            // Read through: a fresh view of the stack's NBT for every operation, so changes made elsewhere are seen.
            LazyOptional<IItemHandler> handler = LazyOptional.of(() -> new ContainerItemHandler(
                    () -> ItemContainers.of(stack, item.containerLayout(stack))));
            event.addCapability(KEY, new ICapabilityProvider() {
                @Override
                public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
                    return ForgeCapabilities.ITEM_HANDLER.orEmpty(capability, handler);
                }
            });
            event.addListener(handler::invalidate);
        });
    }

    /**
     * Drops the item handlers CoolCatLib: Core handed out for the block entity at a position (telling whoever keeps
     * them), so the next lookup finds the current ones. Does nothing when the position isn't loaded.
     */
    public static void invalidate(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) return;
        HandlerProvider provider = BLOCK_PROVIDERS.get(blockEntity);
        if (provider != null) provider.invalidate();
    }

    @FunctionalInterface
    private interface ContainerFinder {
        @Nullable Container find(@Nullable Direction side);
    }

    // A block entity's or entity's handler per side, looked up when asked. A cached handler is kept while the holder
    // hands out the same container, and invalidated (telling whoever keeps it) when it hands out another or none.
    private static final class HandlerProvider implements ICapabilityProvider {
        private final ContainerFinder finder;
        private final @Nullable Container[] containers = new Container[7];
        @SuppressWarnings("unchecked")
        private final @Nullable LazyOptional<IItemHandler>[] handlers = new LazyOptional[7];

        HandlerProvider(ContainerFinder finder) {
            this.finder = finder;
        }

        @Override
        public synchronized <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
            if (capability != ForgeCapabilities.ITEM_HANDLER) return LazyOptional.empty();
            int index = side == null ? 6 : side.ordinal();
            Container container = this.finder.find(side);
            LazyOptional<IItemHandler> cached = this.handlers[index];
            if (cached != null && cached.isPresent() && isSame(this.containers[index], container)) return cached.cast();
            if (cached != null) cached.invalidate();
            this.containers[index] = container;
            if (container == null) {
                this.handlers[index] = null;
                return LazyOptional.empty();
            }
            LazyOptional<IItemHandler> handler = LazyOptional.of(() -> new ContainerItemHandler(() -> container));
            this.handlers[index] = handler;
            return handler.cast();
        }

        synchronized void invalidate() {
            for (int index = 0; index < this.handlers.length; index++) {
                LazyOptional<IItemHandler> handler = this.handlers[index];
                if (handler != null) handler.invalidate();
                this.handlers[index] = null;
                this.containers[index] = null;
            }
        }

        // Face views are made fresh for every lookup; they're the same when they show the same face of the same container.
        private static boolean isSame(@Nullable Container a, @Nullable Container b) {
            if (a instanceof SidedContainerView viewA && b instanceof SidedContainerView viewB) {
                return viewA.getContainer() == viewB.getContainer() && viewA.getSide() == viewB.getSide();
            }
            return a == b;
        }
    }

    // An item handler over a Container, following ItemTransfer's rules: canPlaceItem/canTakeItem (which face views
    // and SlotContainers answer with their faces, roles and filters) and per-slot limits.
    private static final class ContainerItemHandler implements IItemHandlerModifiable {
        private final Supplier<Container> container;

        ContainerItemHandler(Supplier<Container> container) {
            this.container = container;
        }

        @Override
        public int getSlots() {
            return this.container.get().getContainerSize();
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return this.container.get().getItem(slot);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) return stack;
            Container container = this.container.get();
            if (slot < 0 || slot >= container.getContainerSize() || !container.canPlaceItem(slot, stack)) return stack;
            ItemStack existing = container.getItem(slot);
            if (!existing.isEmpty() && !ItemStack.isSameItemSameTags(existing, stack)) return stack;
            int space = ItemTransfer.slotLimit(container, slot, stack) - existing.getCount();
            if (space <= 0) return stack;
            int moved = Math.min(space, stack.getCount());
            if (!simulate) {
                container.setItem(slot, stack.copyWithCount(existing.getCount() + moved));
                container.setChanged();
            }
            return moved == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - moved);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0) return ItemStack.EMPTY;
            Container container = this.container.get();
            if (slot < 0 || slot >= container.getContainerSize()) return ItemStack.EMPTY;
            ItemStack existing = container.getItem(slot);
            if (existing.isEmpty() || !container.canTakeItem(container, slot, existing)) return ItemStack.EMPTY;
            int count = Math.min(amount, existing.getCount());
            if (simulate) return existing.copyWithCount(count);
            ItemStack taken = container.removeItem(slot, count);
            if (!taken.isEmpty()) container.setChanged();
            return taken;
        }

        @Override
        public int getSlotLimit(int slot) {
            return ItemTransfer.slotLimit(this.container.get(), slot, ItemStack.EMPTY);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            Container container = this.container.get();
            return slot >= 0 && slot < container.getContainerSize() && container.canPlaceItem(slot, stack);
        }

        @Override
        public void setStackInSlot(int slot, @NotNull ItemStack stack) {
            Container container = this.container.get();
            container.setItem(slot, stack);
            container.setChanged();
        }
    }
}
