package net.ixdarklord.coolcatcore.api.menu;

import net.ixdarklord.coolcatcore.api.attachment.Attachment;
import net.ixdarklord.coolcatcore.api.block.ExtendedContainerBlockEntity;
import net.ixdarklord.coolcatcore.api.container.ContainerItem;
import net.ixdarklord.coolcatcore.api.container.ContainerLayout;
import net.ixdarklord.coolcatcore.api.container.ItemContainers;
import net.ixdarklord.coolcatcore.api.container.SlotContainer;
import net.ixdarklord.coolcatcore.api.handler.HandlerTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A menu for the inventory of a block or an item, declared once: where its slots go, what else the screen shows, and
 * what its buttons do. It makes the {@link MenuType} to register, opens the menu, and keeps it in sync.
 * <pre>{@code
 * // A 9-slot crate, with the number of times it was used shown on the screen.
 * public static final StorageMenuDefinition CRATE = StorageMenuDefinition.grid(9, 1)
 *         .sync(CRATE_USES)
 *         .button(0, (menu, player) -> menu.getContainer().clearContent())
 *         .build();
 * public static final RegistryEntry<MenuType<StorageMenu>> CRATE_MENU = MENUS.register("crate", CRATE::createMenuType);
 *
 * // Server, when the block is used:
 * CRATE.open(serverPlayer, crateBlockEntity, Component.translatable("container.mymod.crate"));
 * // Client, while it initialises:
 * MenuScreenRegistry.register(CRATE_MENU, StorageScreen::new);
 * }</pre>
 * Slots follow the container's {@link ContainerLayout} (filters, limits, output slots) and move with shift-click; the
 * menu closes when the block is removed or the player walks away, or when an item's stack leaves its slot (which the
 * menu locks meanwhile). Slot contents sync as in any vanilla menu; {@link Builder#sync} values reach the screen too.
 */
public final class StorageMenuDefinition {
    private final List<SlotPlacement> slots;
    private final int inventoryX;
    private final int inventoryY;
    private final int width;
    private final int height;
    private final Map<String, SyncedValue<?>> values;
    private final Map<Integer, BiConsumer<StorageMenu, ServerPlayer>> buttons;
    private @Nullable MenuType<StorageMenu> menuType;

    private StorageMenuDefinition(Builder builder) {
        this.slots = List.copyOf(builder.slots);
        this.inventoryX = builder.inventoryX;
        this.inventoryY = builder.inventoryY;
        this.width = builder.width;
        this.height = builder.height > 0 ? builder.height : builder.inventoryY + 83;
        this.values = Map.copyOf(builder.values);
        this.buttons = Map.copyOf(builder.buttons);
    }

    /** An empty definition: place the slots yourself. The player inventory goes at (8, 84) unless moved. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * The usual chest-like screen: {@code columns} by {@code rows} slots (container slots 0 upward), centred, with the
     * player inventory below them.
     */
    public static Builder grid(int columns, int rows) {
        if (columns < 1 || columns > 9 || rows < 1) throw new IllegalArgumentException("A grid is 1 to 9 columns and at least 1 row");
        int inventoryY = 31 + rows * 18;
        return builder().grid(0, columns, rows, 8 + (9 - columns) * 9, 18).playerInventory(8, inventoryY);
    }

    // --- Registering and opening ---

    /** The menu type to register in {@code Registries.MENU}, e.g. {@code MENUS.register("crate", CRATE::createMenuType)}. */
    public MenuType<StorageMenu> createMenuType() {
        if (this.menuType != null) throw new IllegalStateException("This menu definition already made its menu type");
        this.menuType = ExtendedMenus.create((containerId, inventory, data) -> StorageMenu.readClient(this, containerId, inventory, data));
        return this.menuType;
    }

    public MenuType<StorageMenu> menuType() {
        if (this.menuType == null) throw new IllegalStateException("The menu type wasn't created; register createMenuType");
        return this.menuType;
    }

    /**
     * Opens the menu on a block's inventory: an {@link ExtendedContainerBlockEntity}'s, or any container
     * {@link HandlerTypes#CONTAINER} finds there (a vanilla chest...).
     */
    public void open(ServerPlayer player, BlockEntity blockEntity, Component title) {
        Container container;
        Container opened;
        if (blockEntity instanceof ExtendedContainerBlockEntity holder) {
            container = holder.getContainer();
            opened = holder;
        } else {
            container = HandlerTypes.CONTAINER.find(Objects.requireNonNull(blockEntity.getLevel()), blockEntity.getBlockPos(),
                    blockEntity.getBlockState(), blockEntity, null);
            if (container == null) throw new IllegalArgumentException(blockEntity + " has no container to open");
            opened = container;
        }
        StorageMenu.Source source = StorageMenu.Source.block(blockEntity);
        int size = container.getContainerSize();
        ExtendedMenus.open(player, new SimpleMenuProvider((id, inventory, p) -> new StorageMenu(this, id, inventory, source, container, opened), title),
                buf -> source.write(buf, size));
    }

    /** Opens the menu on the {@link ContainerItem} held in a hand. */
    public void open(ServerPlayer player, InteractionHand hand, Component title) {
        this.open(player, hand == InteractionHand.MAIN_HAND ? player.getInventory().getSelectedSlot() : Inventory.SLOT_OFFHAND, title);
    }

    /** Opens the menu on the {@link ContainerItem} in one of the player's inventory slots. */
    public void open(ServerPlayer player, int inventorySlot, Component title) {
        ItemStack stack = player.getInventory().getItem(inventorySlot);
        if (!(stack.getItem() instanceof ContainerItem item)) throw new IllegalArgumentException(stack + " isn't a ContainerItem");
        this.open(player, inventorySlot, item.containerLayout(stack), title);
    }

    /** Opens the menu on the inventory kept in the stack in one of the player's slots, with the given layout. */
    public void open(ServerPlayer player, int inventorySlot, ContainerLayout layout, Component title) {
        ItemStack stack = player.getInventory().getItem(inventorySlot);
        if (stack.isEmpty()) throw new IllegalArgumentException("Slot " + inventorySlot + " is empty");
        SlotContainer container = ItemContainers.of(stack, layout);
        StorageMenu.Source source = StorageMenu.Source.item(inventorySlot, stack);
        ExtendedMenus.open(player, new SimpleMenuProvider((id, inventory, p) -> new StorageMenu(this, id, inventory, source, container, null), title),
                buf -> source.write(buf, layout.size()));
    }

    // --- Layout ---

    public List<SlotPlacement> slots() {
        return this.slots;
    }

    public int inventoryX() {
        return this.inventoryX;
    }

    public int inventoryY() {
        return this.inventoryY;
    }

    /** The screen's width. */
    public int width() {
        return this.width;
    }

    /** The screen's height. */
    public int height() {
        return this.height;
    }

    Map<String, SyncedValue<?>> values() {
        return this.values;
    }

    @Nullable BiConsumer<StorageMenu, ServerPlayer> button(int id) {
        return this.buttons.get(id);
    }

    /** Where one container slot shows on the screen (the slot's top-left, inside its 18x18 box). */
    public record SlotPlacement(int containerSlot, int x, int y) {}

    record SyncedValue<T>(String name, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Function<StorageMenu, T> getter, T defaultValue) {}

    public static final class Builder {
        private final List<SlotPlacement> slots = new ArrayList<>();
        private int inventoryX = 8;
        private int inventoryY = 84;
        private int width = 176;
        private int height = -1;
        private final Map<String, SyncedValue<?>> values = new LinkedHashMap<>();
        private final Map<Integer, BiConsumer<StorageMenu, ServerPlayer>> buttons = new LinkedHashMap<>();

        private Builder() {}

        /** Shows one container slot at (x, y). */
        public Builder slot(int containerSlot, int x, int y) {
            this.slots.add(new SlotPlacement(containerSlot, x, y));
            return this;
        }

        /** Shows container slots {@code firstSlot} onward in a grid, 18 pixels apart, starting at (x, y). */
        public Builder grid(int firstSlot, int columns, int rows, int x, int y) {
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < columns; column++) {
                    this.slot(firstSlot + row * columns + column, x + column * 18, y + row * 18);
                }
            }
            return this;
        }

        /** Where the player's inventory starts (its hotbar sits 58 pixels lower). */
        public Builder playerInventory(int x, int y) {
            this.inventoryX = x;
            this.inventoryY = y;
            return this;
        }

        /** The screen's size; the height defaults to fit the player inventory. */
        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        /**
         * Shows the holder's value of an attachment (the block entity's or the stack's) on the screen, through
         * {@link StorageMenu#get(Attachment)}, kept up to date while the menu is open, whatever its sync policy.
         */
        public <T> Builder sync(Attachment<T> attachment) {
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec = attachment.streamCodec();
            if (codec == null) {
                if (attachment.codec() == null) throw new IllegalArgumentException(attachment.id() + " can't be sent: it has no codec");
                codec = ByteBufCodecs.fromCodecWithRegistries(attachment.codec());
            }
            return this.put(new SyncedValue<>(attachment.id().toString(), codec, menu -> menu.attachments().get(attachment), attachment.createDefault()));
        }

        /**
         * Shows any value on the screen (a machine's progress...), read on the server each tick and sent when it
         * changes; the client reads it with {@link StorageMenu#getValue(String, Object)}.
         */
        public <T> Builder syncValue(String name, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, T defaultValue, Function<StorageMenu, T> getter) {
            return this.put(new SyncedValue<>(name, codec, getter, defaultValue));
        }

        /**
         * What a button does on the server. The screen presses it with {@code StorageScreen.pressButton(id)}; ids are
         * 0 and up.
         */
        public Builder button(int id, BiConsumer<StorageMenu, ServerPlayer> action) {
            if (id < 0) throw new IllegalArgumentException("Button ids start at 0");
            this.buttons.put(id, action);
            return this;
        }

        private Builder put(SyncedValue<?> value) {
            if (this.values.putIfAbsent(value.name(), value) != null) throw new IllegalArgumentException(value.name() + " is already synced");
            return this;
        }

        public StorageMenuDefinition build() {
            return new StorageMenuDefinition(this);
        }
    }
}
