package net.ixdarklord.coolcatcore.api.container;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.ByteBuf;
import net.ixdarklord.coolcatcore.api.network.codec.ByteBufCodecs;
import net.ixdarklord.coolcatcore.api.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/**
 * The shape of an inventory: how many slots, what each accepts and holds, who reaches it and from which faces.
 * One layout makes any number of {@link SlotContainer}s.
 * <pre>{@code
 * public static final ContainerLayout FURNACE_LIKE = ContainerLayout.builder(3)
 *         .role(0, SlotRole.INPUT)
 *         .filter(1, stack -> stack.is(ItemTags.COALS)).role(1, SlotRole.INPUT)
 *         .role(2, SlotRole.OUTPUT)
 *         .face(Direction.UP, 0).face(Direction.DOWN, 2)   // faces left out reach every slot
 *         .build();
 * }</pre>
 */
public final class ContainerLayout {
    /** The most slots an item stack can keep in its NBT (an {@code Items} list numbers its slots with a byte). */
    public static final int MAX_ITEM_SLOTS = 256;

    private static final Codec<SlotEntry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("slot").forGetter(SlotEntry::slot),
            ItemStack.CODEC.fieldOf("item").forGetter(SlotEntry::stack)
    ).apply(instance, SlotEntry::new));

    private final int size;
    private final int maxStackSize;
    private final Predicate<ItemStack>[] filters;
    private final int[] limits;
    private final SlotRole[] roles;
    private final int[] allSlots;
    private final Map<Direction, int[]> faces;
    private final boolean keepContentsOnBreak;
    private final Codec<SlotContainer> codec;
    private final StreamCodec<FriendlyByteBuf, SlotContainer> streamCodec;

    private ContainerLayout(Builder builder) {
        this.size = builder.size;
        this.maxStackSize = builder.maxStackSize;
        this.filters = builder.filters.clone();
        this.limits = builder.limits.clone();
        this.roles = builder.roles.clone();
        this.keepContentsOnBreak = builder.keepContentsOnBreak;
        // Faces never list slots automation can't reach at all.
        this.allSlots = this.reachable(null);
        this.faces = new EnumMap<>(Direction.class);
        for (Direction side : Direction.values()) {
            int[] slots = builder.faces.get(side);
            this.faces.put(side, slots == null ? this.allSlots : this.reachable(slots));
        }
        this.codec = ENTRY_CODEC.listOf().xmap(entries -> {
            SlotContainer container = this.create();
            for (SlotEntry entry : entries) {
                if (entry.slot >= 0 && entry.slot < this.size) container.load(entry.slot, entry.stack);
            }
            return container;
        }, container -> {
            List<SlotEntry> entries = new ArrayList<>();
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty()) entries.add(new SlotEntry(slot, stack));
            }
            return entries;
        });
        StreamCodec<ByteBuf, SlotContainer> streamCodec = ByteBufCodecs.OPTIONAL_ITEM_STACK.apply(ByteBufCodecs.<ByteBuf, ItemStack>list()).map(items -> {
            SlotContainer container = this.create();
            for (int slot = 0; slot < Math.min(items.size(), this.size); slot++) container.load(slot, items.get(slot));
            return container;
        }, SlotContainer::getItems);
        this.streamCodec = streamCodec.cast();
    }

    private int[] reachable(int @Nullable [] slots) {
        int[] candidates = slots != null ? slots : IntStream.range(0, this.size).toArray();
        return Arrays.stream(candidates).filter(slot -> slot >= 0 && slot < this.size && this.roles[slot] != SlotRole.INTERNAL).toArray();
    }

    public static Builder builder(int size) {
        return new Builder(size);
    }

    /** {@code size} storage slots that take anything. */
    public static ContainerLayout of(int size) {
        return builder(size).build();
    }

    public SlotContainer create() {
        return new SlotContainer(this);
    }

    public int size() {
        return this.size;
    }

    public int maxStackSize() {
        return this.maxStackSize;
    }

    public SlotRole role(int slot) {
        return this.roles[slot];
    }

    /** Whether the slot's filter takes the item. */
    public boolean isItemValid(int slot, ItemStack stack) {
        return slot >= 0 && slot < this.size && this.filters[slot].test(stack);
    }

    /** The most items the slot holds, before the item's own stack size. */
    public int slotLimit(int slot) {
        return Math.min(this.limits[slot], this.maxStackSize);
    }

    /** The slots automation reaches through a face, or through any face for {@code null}. Don't modify it. */
    public int[] slotsForFace(@Nullable Direction side) {
        return side == null ? this.allSlots : this.faces.get(side);
    }

    public boolean exposes(int slot, @Nullable Direction side) {
        for (int reachable : this.slotsForFace(side)) {
            if (reachable == slot) return true;
        }
        return false;
    }

    /**
     * Whether a block keeps the contents in its dropped item (like a shulker box) instead of spilling them. The
     * block entity then also saves them as a vanilla {@code Items} list, which the block's loot table must copy from
     * the block entity into the item, like a shulker box's: a {@code copy_nbt} function from {@code block_entity}
     * with the operation {@code "source": "Items", "target": "BlockEntityTag.Items", "op": "replace"}.
     */
    public boolean keepContentsOnBreak() {
        return this.keepContentsOnBreak;
    }

    /** Saves a container's contents (slot and item per filled slot). */
    public Codec<SlotContainer> codec() {
        return this.codec;
    }

    /** Syncs a container's contents (every slot). */
    public StreamCodec<FriendlyByteBuf, SlotContainer> streamCodec() {
        return this.streamCodec;
    }

    private record SlotEntry(int slot, ItemStack stack) {}

    public static final class Builder {
        private final int size;
        private int maxStackSize = 99;
        private final Predicate<ItemStack>[] filters;
        private final int[] limits;
        private final SlotRole[] roles;
        private final Map<Direction, int[]> faces = new EnumMap<>(Direction.class);
        private boolean keepContentsOnBreak;

        @SuppressWarnings("unchecked")
        private Builder(int size) {
            if (size < 0) throw new IllegalArgumentException("A container can't have " + size + " slots");
            this.size = size;
            this.filters = new Predicate[size];
            Arrays.fill(this.filters, (Predicate<ItemStack>) stack -> true);
            this.limits = new int[size];
            Arrays.fill(this.limits, Integer.MAX_VALUE);
            this.roles = new SlotRole[size];
            Arrays.fill(this.roles, SlotRole.STORAGE);
        }

        /** The most items any slot holds (99 by default, and never more than an item's own stack size). */
        public Builder maxStackSize(int maxStackSize) {
            if (maxStackSize < 1) throw new IllegalArgumentException("maxStackSize must be at least 1");
            this.maxStackSize = maxStackSize;
            return this;
        }

        public Builder filter(int slot, Predicate<ItemStack> filter) {
            return this.filter(slot, slot + 1, filter);
        }

        /** What slots {@code from} (inclusive) to {@code to} (exclusive) accept, from anyone. */
        public Builder filter(int from, int to, Predicate<ItemStack> filter) {
            this.check(from, to);
            for (int slot = from; slot < to; slot++) this.filters[slot] = this.filters[slot].and(filter);
            return this;
        }

        public Builder limit(int slot, int max) {
            return this.limit(slot, slot + 1, max);
        }

        public Builder limit(int from, int to, int max) {
            this.check(from, to);
            if (max < 1) throw new IllegalArgumentException("A slot limit must be at least 1");
            Arrays.fill(this.limits, from, to, max);
            return this;
        }

        public Builder role(int slot, SlotRole role) {
            return this.role(slot, slot + 1, role);
        }

        public Builder role(int from, int to, SlotRole role) {
            this.check(from, to);
            Arrays.fill(this.roles, from, to, role);
            return this;
        }

        /** The only slots automation reaches through {@code side}. Faces not set reach every slot. */
        public Builder face(Direction side, int... slots) {
            for (int slot : slots) this.check(slot, slot + 1);
            this.faces.put(side, slots.clone());
            return this;
        }

        /** See {@link ContainerLayout#keepContentsOnBreak()}. */
        public Builder keepContentsOnBreak() {
            this.keepContentsOnBreak = true;
            return this;
        }

        public ContainerLayout build() {
            if (this.keepContentsOnBreak && this.size > MAX_ITEM_SLOTS) {
                throw new IllegalStateException("Only containers of up to " + MAX_ITEM_SLOTS + " slots fit in an item");
            }
            return new ContainerLayout(this);
        }

        private void check(int from, int to) {
            if (from < 0 || to > this.size || from >= to) {
                throw new IndexOutOfBoundsException("Slots " + from + " to " + to + " are outside a container of " + this.size);
            }
        }
    }
}
