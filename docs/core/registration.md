---
icon: material/shape-plus-outline
description: Deferred registers, commands and client registries
---

# Registration

Registering content from common code. Packages: `net.ixdarklord.coolcatcore.api.registry`, `api.core.commands` and `api.client.registry`, and Canvas's `net.ixdarklord.coolcatcanvas.api.client.registry` for renderers. Commands have [a page of their own](commands.md).

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

### Renderers

The renderer registries are Canvas's, beside its renderers, in `net.ixdarklord.coolcatcanvas.api.client.registry`:

```java
BlockEntityRendererRegistry.register(MyBlockEntities.ALTAR, AltarRenderer::new);
EntityRendererRegistry.register(MyEntities.WISP, WispRenderer::new);
```

For culling, detail by distance and layers, base block entity renderers on Canvas's [`BlockRenderer`](../canvas/block-entity-renderers.md); for animated models, see [Armatures](../canvas/armatures.md).

`ItemRendererRegistry` registers items drawn by code. On 26.x, it registers special item model renderer types, the code-drawn parts of item models (`"type": "minecraft:special"`), by their type's id and the codec that reads them from the item model JSON. Register them before resources load:

```java
ItemRendererRegistry.register(MyMod.id("trophy_icon"), TrophyIconRenderer.Unbaked.MAP_CODEC);
```

```json title="assets/mymod/items/trophy.json"
{
  "model": {
    "type": "minecraft:special",
    "base": "mymod:item/trophy",
    "model": { "type": "mymod:trophy_icon" }
  }
}
```

On 1.21.1 and 1.20.1, which have no item model types, it takes the item and draws it wherever its model is `"parent": "builtin/entity"`, with the model's display transforms already applied:

```java
ItemRendererRegistry.register(MyItems.TROPHY, (stack, context, poseStack, buffers, light, overlay) -> drawTrophy(stack, poseStack, buffers, light));
```

!!! warning "Moved in 9.0.0"
    These were Core's, in `net.ixdarklord.coolcatcore.api.client.registry`, and `ItemRendererRegistry` was `SpecialModelRendererRegistry` on 26.x. The old ones still work but are deprecated.

On 1.21.1 and 1.20.1, Core's `BlockRenderLayers` draws a block's model in another layer than solid, for textures with clear or partly see-through pixels:

```java
BlockRenderLayers.register(MyBlocks.GLASS_CASE, RenderType.translucent());   // or cutout(), cutoutMipped()
```

!!! info "Since"
    `BlockEntityRendererRegistry`: since **26.1.2-4** (and 26.2, 26.3), **8.2.0** on 1.21.1 and 1.20.1, in Canvas since **9.0.0**. `EntityRendererRegistry` and `ItemRendererRegistry` in Canvas: since **9.0.0**, on every version (`ItemRendererRegistry` was `SpecialModelRendererRegistry` since **8.2.0** on 26.x). `BlockRenderLayers`: since **8.2.0**, on 1.21.1 and 1.20.1.

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
| `api.client.registry.BlockRenderLayers` | `register(Supplier<? extends Block> block, RenderType layer)`. 1.21.1 and 1.20.1. |
| Canvas: `api.client.registry.BlockEntityRendererRegistry` | `register(Supplier<? extends BlockEntityType<? extends T>> type, BlockEntityRendererProvider<T, S> provider)`; on 1.21.1 and 1.20.1, `BlockEntityRendererProvider<T>`. |
| Canvas: `api.client.registry.EntityRendererRegistry` | `register(Supplier<? extends EntityType<? extends T>> type, EntityRendererProvider<T> provider)`. |
| Canvas: `api.client.registry.ItemRendererRegistry` | 26.x: `register(Identifier id, MapCodec<? extends SpecialModelRenderer.Unbaked<?>> codec)`. 1.21.1 and 1.20.1: `register(Supplier<? extends ItemLike> item, Renderer renderer)`, where the renderer is `(stack, displayContext, poseStack, buffers, light, overlay) -> ...`. |
