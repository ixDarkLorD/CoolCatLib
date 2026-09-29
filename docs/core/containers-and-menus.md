# Containers and Menus

Inventories that work the same on every loader: slot containers with rules, moving items like automation does, finding a block's or entity's inventory, block entities that tick and hold items, and menus that need no screen code. Packages: `net.ixdarklord.coolcatcore.api.container`, `api.handler`, `api.block`, `api.menu`, `api.client.gui.screens`.

## Slot containers

A `ContainerLayout` describes the slots; a `SlotContainer` holds the items:

```java
public static final ContainerLayout CRATE_LAYOUT = ContainerLayout.builder(9)
        .filter(0, 8, stack -> !stack.is(Items.BEDROCK))   // slots 0-7 refuse bedrock
        .role(8, SlotRole.OUTPUT)                          // slot 8: automation can only take out
        .face(Direction.DOWN, 8)                           // hoppers below only see slot 8
        .build();

SlotContainer container = CRATE_LAYOUT.create();
ItemStack rest = container.insert(new ItemStack(Items.IRON_INGOT, 5), false);   // returns what didn't fit
ItemStack taken = container.extract(stack -> stack.is(Items.IRON_INGOT), 3, false);
```

Store one on anything with an attachment: `ATTACHMENTS.container("crate_inventory", CRATE_LAYOUT)` (see [Attachments](attachments.md)). For items, implement `ContainerItem` on the item, and its contents live in the stack's `minecraft:container` component: `ItemContainers.of(stack)`.

## Finding and moving items

```java
// Any inventory at a position, as seen from a face (vanilla containers, double chests, composters, your own...):
Container top = HandlerTypes.CONTAINER.find(level, pos, Direction.UP);
ItemStack rest = ItemTransfer.insert(top, new ItemStack(Items.IRON_INGOT, 5), false);
ItemTransfer.move(from, to, 16);

// For repeated lookups, cache it; it's looked up again when the block or block entity there changes:
BlockHandlerCache<Container> cache = HandlerTypes.CONTAINER.createCache(level, pos, Direction.UP);
Container current = cache.get();
```

You can define your own handler types with `HandlerType.create(id, MyHandler.class)`, and provide them for blocks, block entities, entities and items.

## Block entities

```java
public static class CrateBlockEntity extends ExtendedContainerBlockEntity {
    public CrateBlockEntity(BlockPos pos, BlockState state) {
        super(MyRegistries.CRATE_BE.get(), pos, state, MyAttachments.CRATE_INVENTORY);
    }

    @Override
    protected void serverTick(ServerLevel level) { /* runs every tick on the server */ }
}

// In the block:
@Override
public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
    return ExtendedBlockEntity.ticker(type, MyRegistries.CRATE_BE.get());
}
```

`ExtendedContainerBlockEntity` is a `WorldlyContainer` backed by the attachment. Hoppers and other mods see it through the layout's roles and faces. Its contents spill when it's broken, unless the layout says `keepContentsOnBreak()`.

## Storage menus

`StorageMenuDefinition` describes a menu's slots and synced values. `StorageScreen` draws it without any texture:

```java
public static final StorageMenuDefinition CRATE_MENU_DEF = StorageMenuDefinition.grid(9, 1)
        .syncValue("free_slots", ByteBufCodecs.VAR_INT, 0, menu -> countFreeSlots(menu.getContainer()))
        .button(0, (menu, player) -> menu.getContainer().clearContent())
        .build();

public static final RegistryEntry<MenuType<StorageMenu>> CRATE_MENU = MENUS.register("crate", CRATE_MENU_DEF::createMenuType);

// Open it (server side), e.g. from Block#useWithoutItem:
CRATE_MENU_DEF.open(serverPlayer, crateBlockEntity, Component.literal("Crate"));

// Client constructor:
MenuScreenRegistry.register(CRATE_MENU, StorageScreen::new);
```

For menus of your own, `ExtendedMenus.create(factory)` makes a `MenuType` whose factory reads extra data, and `ExtendedMenus.open(player, provider, buf -> ...)` writes it.

## Reference

| Type | Description |
|---|---|
| `ContainerLayout` | `builder(size)` / `of(size)`: `maxStackSize` (default 99), `filter`, `limit`, `role`, `face`, `keepContentsOnBreak`; `create()`, `codec()`, `streamCodec()`. |
| `SlotRole` | `STORAGE`, `INPUT`, `OUTPUT`, `INTERNAL`: what players and automation may do with a slot. |
| `SlotContainer` | A `Container` with rules: `insert`, `extract`, `count`, `canPlayerPlace`, `canInsertFrom`, `canExtractFrom`, `copyItems`, `setItems`, `toContents`, `dropContents`, `comparatorSignal`, `createSlot(slot, x, y)`, `addListener`, `setValidator`. |
| `ContainerSlot` | A menu `Slot` that enforces the layout. |
| `ContainerItem`, `ItemContainers` | Items with an inventory: `containerLayout(stack)`, `ItemContainers.of(stack[, layout])`. |
| `ItemTransfer` | `insert`, `extract`, `move`, `slotLimit` on any `Container`, optionally through a face. |
| `SidedContainerView` | A `WorldlyContainer` seen from one side. |
| `api.handler.HandlerType<T>` | `create(id, class)`; `registerBlock`, `registerBlockEntity`, `registerEntity`, `registerItem` (plus fallbacks); `find(level, pos, side)`, `find(entity)`, `find(stack)`; `createCache(...)`, `invalidate(level, pos)`. |
| `api.handler.HandlerTypes` | `CONTAINER`: the built-in inventory lookup. |
| `api.handler.HandlerProvider`, `ItemHandlerProvider`, `HandlerMap`, `BlockHandlerCache` | Expose handlers from your own block entities, entities and items. |
| `api.block.ExtendedBlockEntity` | A block entity with attachment shortcuts (`get`, `set`, `update`, `modify`), handlers, `serverTick`/`clientTick`, and `ticker(type, expected)`. |
| `api.block.ExtendedContainerBlockEntity` | The same, holding a `SlotContainer` attachment as a `WorldlyContainer`. |
| `api.menu.ExtendedMenus` | `create(factory)`, `open(player, provider[, data])`. |
| `api.menu.StorageMenuDefinition` | `grid(columns, rows)` / `builder()`: `slot`, `grid`, `playerInventory`, `size`, `sync(attachment)`, `syncValue(...)`, `button(id, action)`; `createMenuType()`, `open(player, blockEntity / hand / inventorySlot, title)`. |
| `api.menu.StorageMenu` | The menu: `getContainer()`, `getSource()`, `get(attachment)` (synced on clients), `getValue(name, default)`. |
| `api.client.gui.screens.StorageScreen` | A texture-free screen for storage menus; extend it to add panels (`extractPanel`) and buttons (`pressButton`). |
