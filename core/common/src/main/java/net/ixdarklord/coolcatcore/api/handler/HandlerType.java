package net.ixdarklord.coolcatcore.api.handler;

import net.ixdarklord.coolcatcore.internal.handler.HandlerCaches;
import net.ixdarklord.coolcatcore.internal.platform.CommonServices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A kind of handler blocks, entities and items can hand out: something other code works with without knowing the
 * holder (an inventory, an energy buffer, a fluid tank...).
 * <pre>{@code
 * public static final HandlerType<EnergyStorage> ENERGY = HandlerType.create(id("energy"), EnergyStorage.class);
 *
 * // Offer it: implement HandlerProvider (ExtendedBlockEntity does, through its HandlerMap), or register a provider,
 * // also for blocks, entities and items from other mods.
 * ENERGY.registerBlockEntity(MY_GENERATOR.get(), (generator, side) -> generator.energy());
 *
 * // Use it:
 * EnergyStorage storage = ENERGY.find(level, pos, Direction.UP);
 * BlockHandlerCache<EnergyStorage> cache = ENERGY.createCache(level, pos, Direction.UP); // for repeated lookups
 * }</pre>
 * Lookups ask, in order: the holder itself ({@link HandlerProvider}, {@link ItemHandlerProvider}), the providers
 * registered for its block, block entity type, entity type or item, then the fallbacks. The first non-null wins.
 *
 * @param <T> the handler's type
 */
public final class HandlerType<T> {
    private static final Map<Identifier, HandlerType<?>> TYPES = new ConcurrentHashMap<>();

    private final Identifier id;
    private final Class<T> handlerClass;
    private final Map<Block, List<BlockProvider<T>>> blockProviders = new ConcurrentHashMap<>();
    private final Map<BlockEntityType<?>, List<BlockEntityProvider<?, T>>> blockEntityProviders = new ConcurrentHashMap<>();
    private final List<BlockProvider<T>> blockFallbacks = new CopyOnWriteArrayList<>();
    private final Map<EntityType<?>, List<EntityProvider<?, T>>> entityProviders = new ConcurrentHashMap<>();
    private final List<EntityProvider<Entity, T>> entityFallbacks = new CopyOnWriteArrayList<>();
    private final Map<Item, List<ItemProvider<T>>> itemProviders = new ConcurrentHashMap<>();
    private final List<ItemProvider<T>> itemFallbacks = new CopyOnWriteArrayList<>();

    private HandlerType(Identifier id, Class<T> handlerClass) {
        this.id = id;
        this.handlerClass = handlerClass;
    }

    /** Creates a handler type; each id may be used once. */
    public static <T> HandlerType<T> create(Identifier id, Class<T> handlerClass) {
        HandlerType<T> type = new HandlerType<>(Objects.requireNonNull(id, "id"), Objects.requireNonNull(handlerClass, "handlerClass"));
        if (TYPES.putIfAbsent(id, type) != null) throw new IllegalArgumentException("A handler type with the id " + id + " already exists");
        return type;
    }

    public static @Nullable HandlerType<?> byId(Identifier id) {
        return TYPES.get(id);
    }

    public Identifier id() {
        return this.id;
    }

    public Class<T> handlerClass() {
        return this.handlerClass;
    }

    // --- Registering ---

    public HandlerType<T> registerBlock(BlockProvider<T> provider, Block... blocks) {
        for (Block block : blocks) this.blockProviders.computeIfAbsent(block, key -> new CopyOnWriteArrayList<>()).add(provider);
        return this;
    }

    public <BE extends BlockEntity> HandlerType<T> registerBlockEntity(BlockEntityType<BE> type, BlockEntityProvider<? super BE, T> provider) {
        this.blockEntityProviders.computeIfAbsent(type, key -> new CopyOnWriteArrayList<>()).add(provider);
        return this;
    }

    /** Asked for every block the registered providers had nothing for. */
    public HandlerType<T> registerBlockFallback(BlockProvider<T> provider) {
        this.blockFallbacks.add(provider);
        return this;
    }

    public <E extends Entity> HandlerType<T> registerEntity(EntityType<E> type, EntityProvider<? super E, T> provider) {
        this.entityProviders.computeIfAbsent(type, key -> new CopyOnWriteArrayList<>()).add(provider);
        return this;
    }

    public HandlerType<T> registerEntityFallback(EntityProvider<Entity, T> provider) {
        this.entityFallbacks.add(provider);
        return this;
    }

    public HandlerType<T> registerItem(ItemProvider<T> provider, ItemLike... items) {
        for (ItemLike item : items) this.itemProviders.computeIfAbsent(item.asItem(), key -> new CopyOnWriteArrayList<>()).add(provider);
        return this;
    }

    public HandlerType<T> registerItemFallback(ItemProvider<T> provider) {
        this.itemFallbacks.add(provider);
        return this;
    }

    // --- Finding ---

    /** The handler of the block at a position, through a face ({@code null} for the block as a whole). */
    public @Nullable T find(Level level, BlockPos pos, @Nullable Direction side) {
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        return this.find(level, pos, state, blockEntity, side);
    }

    @SuppressWarnings("unchecked")
    public @Nullable T find(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, @Nullable Direction side) {
        if (blockEntity instanceof HandlerProvider provider) {
            T handler = provider.getHandler(this, side);
            if (handler != null) return handler;
        }
        List<BlockProvider<T>> providers = this.blockProviders.get(state.getBlock());
        if (providers != null) {
            for (BlockProvider<T> provider : providers) {
                T handler = provider.get(level, pos, state, blockEntity, side);
                if (handler != null) return handler;
            }
        }
        if (blockEntity != null) {
            List<BlockEntityProvider<?, T>> byType = this.blockEntityProviders.get(blockEntity.getType());
            if (byType != null) {
                for (BlockEntityProvider<?, T> provider : byType) {
                    T handler = ((BlockEntityProvider<BlockEntity, T>) provider).get(blockEntity, side);
                    if (handler != null) return handler;
                }
            }
        }
        for (BlockProvider<T> provider : this.blockFallbacks) {
            T handler = provider.get(level, pos, state, blockEntity, side);
            if (handler != null) return handler;
        }
        return null;
    }

    public @Nullable T find(Entity entity) {
        return this.find(entity, null);
    }

    /** The entity's handler, through a face for entities that tell them apart (a minecart's hopper side). */
    @SuppressWarnings("unchecked")
    public @Nullable T find(Entity entity, @Nullable Direction side) {
        if (entity instanceof HandlerProvider provider) {
            T handler = provider.getHandler(this, side);
            if (handler != null) return handler;
        }
        List<EntityProvider<?, T>> providers = this.entityProviders.get(entity.getType());
        if (providers != null) {
            for (EntityProvider<?, T> provider : providers) {
                T handler = ((EntityProvider<Entity, T>) provider).get(entity, side);
                if (handler != null) return handler;
            }
        }
        for (EntityProvider<Entity, T> provider : this.entityFallbacks) {
            T handler = provider.get(entity, side);
            if (handler != null) return handler;
        }
        return null;
    }

    public @Nullable T find(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.getItem() instanceof ItemHandlerProvider provider) {
            T handler = provider.getHandler(this, stack);
            if (handler != null) return handler;
        }
        List<ItemProvider<T>> providers = this.itemProviders.get(stack.getItem());
        if (providers != null) {
            for (ItemProvider<T> provider : providers) {
                T handler = provider.get(stack);
                if (handler != null) return handler;
            }
        }
        for (ItemProvider<T> provider : this.itemFallbacks) {
            T handler = provider.get(stack);
            if (handler != null) return handler;
        }
        return null;
    }

    /**
     * A cache for looking up the same position repeatedly (a pipe's neighbour). It notices on its own when the block
     * or block entity there changes; holders that change what they hand out call {@link #invalidate}.
     */
    public BlockHandlerCache<T> createCache(Level level, BlockPos pos, @Nullable Direction side) {
        return new BlockHandlerCache<>(this, level, pos, side, null);
    }

    /** Like {@link #createCache(Level, BlockPos, Direction)}, running {@code onInvalidate} when it's invalidated. */
    public BlockHandlerCache<T> createCache(Level level, BlockPos pos, @Nullable Direction side, Runnable onInvalidate) {
        return new BlockHandlerCache<>(this, level, pos, side, onInvalidate);
    }

    /**
     * Tells every cache at a position (of any handler type, CoolCatLib: Core's and the loader's) to look again: call it
     * when a block starts or stops handing out a handler, or hands out a different one.
     */
    public static void invalidate(Level level, BlockPos pos) {
        HandlerCaches.invalidate(level, pos);
        CommonServices.get().invalidateBlockHandlers(level, pos);
    }

    @Override
    public String toString() {
        return "HandlerType[" + this.id + "]";
    }

    @FunctionalInterface
    public interface BlockProvider<T> {
        @Nullable T get(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, @Nullable Direction side);
    }

    @FunctionalInterface
    public interface BlockEntityProvider<BE extends BlockEntity, T> {
        @Nullable T get(BE blockEntity, @Nullable Direction side);
    }

    @FunctionalInterface
    public interface EntityProvider<E extends Entity, T> {
        @Nullable T get(E entity, @Nullable Direction side);
    }

    @FunctionalInterface
    public interface ItemProvider<T> {
        @Nullable T get(ItemStack stack);
    }
}
