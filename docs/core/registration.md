# Registration

Registering content from common code. Packages: `net.ixdarklord.coolcatcore.api.registry`, `api.core.commands` and `api.client.registry`.

## Deferred registers

```java
public final class MyRegistries {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(MyMod.MOD_ID, Registries.ITEM);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(MyMod.MOD_ID, Registries.BLOCK);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(MyMod.MOD_ID, Registries.CREATIVE_MODE_TAB);

    public static final RegistryEntry<Block> RUBY_BLOCK = BLOCKS.register("ruby_block", () ->
            new Block(BlockBehaviour.Properties.of().strength(3.0F).setId(ResourceKey.create(Registries.BLOCK, MyMod.id("ruby_block")))));

    public static final RegistryEntry<CreativeModeTab> TAB = TABS.register("main", () -> CreativeTabs.builder()
            .title(Component.translatable("itemGroup.mymod"))
            .icon(() -> new ItemStack(RUBY_BLOCK.get()))
            .displayItems((parameters, output) -> output.accept(RUBY_BLOCK.get()))
            .build());
}

// In ModConstructor#onConstructMod():
MyRegistries.BLOCKS.register();
MyRegistries.ITEMS.register();
MyRegistries.TABS.register();
```

Call `register()` once, during construction. On NeoForge, entries added after that are never registered.

## Other registration helpers

```java
// A block entity type, from its factory and blocks:
BLOCK_ENTITIES.register("crate", () -> BlockEntityTypes.create(CrateBlockEntity::new, CRATE.get()));

// A server reload listener (world load and /reload):
ReloadListeners.registerServer(MyMod.id("challenges"), new ChallengesManager());

// A custom command argument type, through a DeferredRegister of Registries.COMMAND_ARGUMENT_TYPE:
public static final RegistryEntry<ArgumentTypeInfo<ShapeArgument, ?>> SHAPE_ARGUMENT = ARGUMENT_TYPES.register("shape",
        () -> ArgumentTypeRegistry.register(ShapeArgument.class, SingletonArgumentInfo.contextFree(ShapeArgument::shape)));
```

## Client registries

Register these from `ClientModConstructor#onConstructMod()`:

```java
KeyMappingRegistry.register(MY_KEY);
MenuScreenRegistry.register(MyRegistries.CRATE_MENU, CrateScreen::new);
TooltipComponentRegistry.register(MyTooltip.class, MyClientTooltip::new);
```

## Reference

| Type | Description |
|---|---|
| `DeferredRegister<T>` | `create(modId, registryKey)`, `register(name, supplier)` → `RegistryEntry`, `register()`, `getEntries()`, `getModId()`, `getRegistryKey()`. |
| `RegistryEntry<T>` | A `Supplier<T>`: `get()` (throws before registration), `getId()`, `holder()`, `isBound()`. |
| `CreativeTabs` | `builder()`: a `CreativeModeTab.Builder` that the loader places; register the tab in `Registries.CREATIVE_MODE_TAB`. |
| `ReloadListeners` | `registerServer(Identifier id, PreparableReloadListener listener)`. |
| `BlockEntityTypes` | `create(Factory<T> factory, Block... blocks)`, where `Factory` is `(BlockPos, BlockState) -> T`. |
| `api.core.commands.ArgumentTypeRegistry` | `register(Class<A> argumentClass, I argumentTypeInfo)`. |
| `api.client.registry.KeyMappingRegistry` | `register(KeyMapping)` → the mapping. |
| `api.client.registry.MenuScreenRegistry` | `register(Supplier<MenuType<M>> type, ScreenFactory<M, U> factory)`, where the factory is `(menu, inventory, title) -> screen`. |
| `api.client.registry.TooltipComponentRegistry` | `register(Class<T> type, Function<T, ClientTooltipComponent> factory)`. |
