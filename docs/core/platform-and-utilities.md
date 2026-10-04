# Platform and Utilities

Platform information, brewing recipes, datagen helpers and small utilities. Packages: `net.ixdarklord.coolcatcore.api.platform`, `api.hooks`, `api.brewing`, `api.core`, `api.datagen.language`, `api.item`, `api.data`, `api.utils`, `api.client.utils`.

## Platform

```java
if (Platform.isModLoaded("jei")) { /* optional integration */ }
if (Platform.isDevelopmentEnvironment()) { /* dev-only commands */ }
Path configs = Platform.getConfigFolder();
MinecraftServer server = Platform.getServer();          // null on a client with no server running
Platform.runOnClient(() -> MyClientHooks::init);        // never loads the class on a dedicated server
boolean fake = Platform.isFakePlayer(player);
```

| Method | Description |
|---|---|
| `getLoader()`, `isFabric()`, `isNeoForge()` | The loader (`Platform.Loader`). The 1.21.1 and 1.20.1 versions add `FORGE` and `isForge()`. |
| `isModLoaded(modId)`, `getModName(modId)` | Other mods. |
| `isDevelopmentEnvironment()`, `getEnvironment()`, `isClient()` | Where the game is running (`Env.CLIENT` or `Env.SERVER`). |
| `getGameFolder()`, `getConfigFolder()` | Folders. |
| `runOnClient(Supplier<Runnable>)`, `getServer()`, `isFakePlayer(player)` | Side helpers. |
| `api.hooks.ServerLifecycleHooks` | `getCurrentServer()`, `isServerAvailable()`. |

## Brewing recipes

```java
RegisterBrewingRecipesEvent.EVENT.register(event -> event.getBuilder().addRecipe(
        Ingredient.of(Items.POTION), Ingredient.of(Items.RAW_COPPER), Items.COPPER_INGOT.getDefaultInstance()));
```

The event is `api.event.v1.server.RegisterBrewingRecipesEvent`. For recipes with custom matching, implement `IBrewingRecipe` (or use the `BrewingRecipe` record) and pass it to `BrewingBuilder.addRecipe(recipe)`.

## Data generation

A `DataGenerationConstructor` adds providers; see [Getting Started](../getting-started.md) for hooking it up:

```java
public class MyModData implements DataGenerationConstructor {
    @Override
    public void onGatherData(DataGenerationContext context) {
        context.addProvider((output, registries) -> new MyRecipeProvider(output, registries));
        context.addProvider(output -> new MyModelProvider(output));
    }
}
```

`api.datagen.language.LanguageProvider` writes language files from common code. Implement `addTranslations()` with its `add(...)` overloads for items, effects, key mappings, attributes, entity types, creative tabs, potions, enchantments and Curios/Trinkets slots. Then wrap it per loader: `FabricLanguageWrapper.factory(MyLang::new)` on Fabric, or `NeoForgeLanguageWrapper.factory(modId, MyLang::new)` on NeoForge.

## Items and data components

| Type | Description |
|---|---|
| `api.item.ComponentItem` | An item with a category line (`ComponentType.CRAFTING`, `TOOLS`, `ABILITY`) and "hold Shift" tooltips (`isShiftButtonNotPressed(consumer)`). |
| `api.item.DecoratedItem` | An item that draws over itself in GUIs. See [Item Decorators](item-decorators.md). |
| `api.data.DataComponent<T>`, `api.data.ItemDataComponent<T>` | Base classes for data-component values: `setStack(stack)`, then `save()` writes the value back to the stack. |

## Utilities

| Type | Description |
|---|---|
| `CodecUtils` | `ITEM_CODEC`, `COLOR_CODEC`; encode and decode to tags, compounds, lists and maps (the `*Safe` variants don't throw). |
| `ColorUtils` | An RGB value: channels, `blend`, `multiBlend`, conversion to and from `java.awt.Color`. |
| `ComponentHelper` | `toComponent`, `toString`, `limitComponent`, `splitComponent`. |
| `ContainerHelper` | Search a `Container` or a player's inventory: `containsItem`, `findFirstSlotWithItem`, `getAllMatchingStacks`, `getAllMatchingSlots`. |
| `SlotReference` | A reference to a slot in a player's inventory or a container: `get()`, `set(stack)`, `clear()`. |
| `ChatFormattingUtils`, `ChatFormattingMapping` | Colors by progress (`getProgressColor(current, max)`). |
| `JsonUtils` | `deepMerge(a, b)`. |
| `MathUtils` | `cycledBetweenValues(min, max, speed, time, reverse)`. |
| `ParticleTypes` | `simple()` and `complex(codec, streamCodec)` particle types. |
| `RecipeHelper` | Shaped and shapeless matching helpers. |
| `ValueConverter` | Parse and format strings, ints, doubles, booleans and enums. |
| `KeysUtils` (also in `api.client.utils`) | Checks for a held three-key combination (`isHolden3ComboButtons()`). |
| `api.client.utils.MouseHelper` | `isMouseOver(...)`, `getMouseX()`, `getMouseY()`. |
