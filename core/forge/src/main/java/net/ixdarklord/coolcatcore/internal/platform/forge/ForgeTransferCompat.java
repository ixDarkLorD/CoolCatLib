package net.ixdarklord.coolcatcore.internal.platform.forge;

import net.ixdarklord.coolcatcore.api.block.ExtendedContainerBlockEntity;
import net.ixdarklord.coolcatcore.api.container.ContainerItem;
import net.ixdarklord.coolcatcore.api.container.ContainerLayout;
import net.ixdarklord.coolcatcore.api.container.SidedContainerView;
import net.ixdarklord.coolcatcore.api.handler.HandlerTypes;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

// Exposes CoolCatLib: Core containers as Forge item handler capabilities, so other mods' pipes and tools reach them:
// block entities and entities through HandlerTypes.CONTAINER, ContainerItem stacks through their minecraft:container
// component. Vanilla containers are left to Forge's own providers.
public final class ForgeTransferCompat {
    private static final ResourceLocation ID = CoolCatCore.rl("container");
    // The block providers by block entity, for HandlerType.invalidate.
    private static final Map<BlockEntity, CachingProvider> BLOCK_PROVIDERS = Collections.synchronizedMap(new WeakHashMap<>());

    private ForgeTransferCompat() {}

    public static void register(IEventBus bus) {
        attach(bus, BlockEntity.class, event -> {
            BlockEntity blockEntity = event.getObject();
            if (blockEntity instanceof Container && !(blockEntity instanceof ExtendedContainerBlockEntity)) return;
            CachingProvider provider = new CachingProvider(side -> blockContainer(blockEntity, side));
            BLOCK_PROVIDERS.put(blockEntity, provider);
            event.addCapability(ID, provider);
            event.addListener(provider::invalidate);
        });
        attach(bus, Entity.class, event -> {
            Entity entity = event.getObject();
            if (entity instanceof Container) return;
            CachingProvider provider = new CachingProvider(side -> HandlerTypes.CONTAINER.find(entity, side));
            event.addCapability(ID, provider);
            event.addListener(provider::invalidate);
        });
        attach(bus, ItemStack.class, event -> {
            ItemStack stack = event.getObject();
            if (stack.getItem() instanceof ContainerItem item) {
                LazyOptional<IItemHandler> handler = LazyOptional.of(() -> new LayoutItemHandler(stack, item.containerLayout(stack)));
                event.addCapability(ID, new ICapabilityProvider() {
                    @Override
                    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
                        return ForgeCapabilities.ITEM_HANDLER.orEmpty(capability, handler);
                    }
                });
                event.addListener(handler::invalidate);
            }
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> void attach(IEventBus bus, Class<T> type, Consumer<AttachCapabilitiesEvent<T>> listener) {
        bus.addGenericListener(type, EventPriority.NORMAL, false, (Class<AttachCapabilitiesEvent<T>>) (Class) AttachCapabilitiesEvent.class, listener);
    }

    /** Drops the handlers handed out for the block entity at a position, so the next query finds them again. */
    public static void invalidate(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) return;
        CachingProvider provider = BLOCK_PROVIDERS.get(blockEntity);
        if (provider != null) provider.invalidate();
    }

    private static @Nullable Container blockContainer(BlockEntity blockEntity, @Nullable Direction side) {
        Level level = blockEntity.getLevel();
        if (level == null) return null;
        return HandlerTypes.CONTAINER.find(level, blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, side);
    }

    private static IItemHandler wrap(Container container, @Nullable Direction side) {
        // Forge's sided wrapper applies the face rules itself.
        if (container instanceof SidedContainerView view) return new SidedInvWrapper(view.getContainer(), view.getSide());
        if (container instanceof WorldlyContainer worldly) return new SidedInvWrapper(worldly, side);
        return new InvWrapper(container);
    }

    // The container behind a face, looked up on each query like NeoForge's capability providers are; the handler is
    // kept (and so stays the same LazyOptional for whoever listens to it) while the same container is found.
    private static final class CachingProvider implements ICapabilityProvider {
        private final ContainerFinder finder;
        private final Map<Direction, Cached> cache = new HashMap<>();

        CachingProvider(ContainerFinder finder) {
            this.finder = finder;
        }

        @Override
        public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
            if (capability != ForgeCapabilities.ITEM_HANDLER) return LazyOptional.empty();
            Container container = this.finder.find(side);
            synchronized (this.cache) {
                Cached cached = this.cache.get(side);
                if (container == null) {
                    if (cached != null) {
                        this.cache.remove(side);
                        cached.handler().invalidate();
                    }
                    return LazyOptional.empty();
                }
                Object source = container instanceof SidedContainerView view ? view.getContainer() : container;
                if (cached == null || cached.source() != source) {
                    if (cached != null) cached.handler().invalidate();
                    IItemHandler handler = wrap(container, side);
                    cached = new Cached(source, LazyOptional.of(() -> handler));
                    this.cache.put(side, cached);
                }
                return cached.handler().cast();
            }
        }

        void invalidate() {
            synchronized (this.cache) {
                this.cache.values().forEach(cached -> cached.handler().invalidate());
                this.cache.clear();
            }
        }
    }

    @FunctionalInterface
    private interface ContainerFinder {
        @Nullable Container find(@Nullable Direction side);
    }

    private record Cached(Object source, LazyOptional<IItemHandler> handler) {}

    // A ContainerItem stack's contents, changed on the stack itself. Follows the layout: filters and roles decide what
    // goes in and out, limits how much.
    private static final class LayoutItemHandler implements IItemHandlerModifiable {
        private final ItemStack stack;
        private final ContainerLayout layout;
        private final int size;

        LayoutItemHandler(ItemStack stack, ContainerLayout layout) {
            this.stack = stack;
            this.layout = layout;
            this.size = Math.min(layout.size(), ContainerLayout.MAX_ITEM_SLOTS);
        }

        private NonNullList<ItemStack> items() {
            NonNullList<ItemStack> items = NonNullList.withSize(this.size, ItemStack.EMPTY);
            this.stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
            return items;
        }

        private void setItems(NonNullList<ItemStack> items) {
            this.stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        }

        private void validateSlot(int slot) {
            if (slot < 0 || slot >= this.size) throw new IllegalArgumentException("Slot " + slot + " not in valid range - [0," + this.size + ")");
        }

        @Override
        public int getSlots() {
            return this.size;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            this.validateSlot(slot);
            return this.items().get(slot);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) return ItemStack.EMPTY;
            this.validateSlot(slot);
            if (!this.isItemValid(slot, stack)) return stack;
            NonNullList<ItemStack> items = this.items();
            ItemStack existing = items.get(slot);
            if (!existing.isEmpty() && !ItemHandlerHelper.canItemStacksStack(stack, existing)) return stack;
            int limit = Math.min(this.getSlotLimit(slot), stack.getMaxStackSize()) - existing.getCount();
            if (limit <= 0) return stack;
            int amount = Math.min(limit, stack.getCount());
            if (!simulate) {
                items.set(slot, stack.copyWithCount(existing.getCount() + amount));
                this.setItems(items);
            }
            return amount == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - amount);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0) return ItemStack.EMPTY;
            this.validateSlot(slot);
            if (!this.layout.role(slot).automationExtract()) return ItemStack.EMPTY;
            NonNullList<ItemStack> items = this.items();
            ItemStack existing = items.get(slot);
            if (existing.isEmpty()) return ItemStack.EMPTY;
            int extracted = Math.min(amount, Math.min(existing.getCount(), existing.getMaxStackSize()));
            if (!simulate) {
                items.set(slot, extracted == existing.getCount() ? ItemStack.EMPTY : existing.copyWithCount(existing.getCount() - extracted));
                this.setItems(items);
            }
            return existing.copyWithCount(extracted);
        }

        @Override
        public int getSlotLimit(int slot) {
            return Math.min(Item.ABSOLUTE_MAX_STACK_SIZE, this.layout.slotLimit(slot));
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return stack.getItem().canFitInsideContainerItems() && this.layout.role(slot).automationInsert()
                    && this.layout.isItemValid(slot, stack);
        }

        @Override
        public void setStackInSlot(int slot, @NotNull ItemStack stack) {
            this.validateSlot(slot);
            NonNullList<ItemStack> items = this.items();
            items.set(slot, stack);
            this.setItems(items);
        }
    }
}
