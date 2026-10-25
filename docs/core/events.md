---
icon: material/lightning-bolt-outline
description: Cross-loader events from common code
---

# Events

Cross-loader events: register listeners in common code, and CoolCatLib bridges them to each loader's own events. Package: `net.ixdarklord.coolcatcore.api.event.v2` (`core`, `common`, `client`).

!!! warning
    `api.event.v2` is marked `@UnstableApi`: event names may still change between releases.

## Listening

Every event is an `EventInvoker` constant on a holder class:

```java
ServerTickEvents.END.register(server -> { /* after every server tick */ });

PlayerEvents.JOIN.register(player -> player.sendSystemMessage(Component.literal("Welcome!")));

// Returning an interrupting result cancels the break.
BlockEvents.BREAK.register((level, pos, state, player) ->
        state.is(Blocks.BEDROCK) ? EventResult.interrupt() : EventResult.pass());

CommandEvents.REGISTER.register((dispatcher, context, selection) ->
        dispatcher.register(Commands.literal("hello").executes(ctx -> 1)));

// Order listeners with phases (FIRST, EARLY, DEFAULT, LATE, LAST).
ServerTickEvents.END.register(EventPhase.LATE, server -> { });

// Stop a player picking up a locked item; it stays on the ground.
PlayerEvents.PICKUP_ITEM.register((player, item) ->
        isLocked(player, item.getItem()) ? EventResult.interrupt() : EventResult.pass());

// Events can also be found by their listener type.
EventInvoker.lookup(ServerTickEvents.End.class).register(server -> { });
```

### Phases and other mods

Phases order CoolCatLib's own listeners. Where the loader has the same event, they also reach other mods' listeners of it, by their **band** (`EventPhase.band()`): `FIRST` and earlier run before other mods' Forge, NeoForge or Fabric listeners, `LAST` and later after them, and the phases between with them. Events whose loader hook fires each band at its own place say so; the rest run all their listeners at once.

## Your own events

`EventInvoker.create` makes an event from a functional interface, and combines listeners by the method's return type:

| Return type | How listeners run |
|---|---|
| `void` | Every listener runs. |
| `EventResult` | Until one interrupts (anything but `PASS`); that result is returned, else `PASS`. |
| `EventResultHolder<T>` | The same, carrying a value. |
| `boolean` | Until one returns `false`. |

```java
@FunctionalInterface
public interface ShapeUnlocked { void onUnlocked(ServerPlayer player, Identifier shape); }

public static final EventInvoker<ShapeUnlocked> SHAPE_UNLOCKED = EventInvoker.create(ShapeUnlocked.class);

// Fire it:
SHAPE_UNLOCKED.invoker().onUnlocked(player, shapeId);
```

For custom combining, use `EventInvoker.create(Class<T> type, Function<List<T>, T> combiner)`.

## Built-in events

### Common (`api.event.v2.common`)

| Holder | Event | Listener |
|---|---|---|
| `ServerLifecycleEvents` | `STARTING`, `STARTED`, `STOPPING`, `STOPPED` | `(MinecraftServer)` |
| | `SYNC_DATA_PACK_CONTENTS` | `(ServerPlayer player, boolean joined)`: fires per player on login (`joined` is true) and for every player after `/reload`. Send datapack-loaded data to clients here. |
| `ServerTickEvents` | `START`, `END` | `(MinecraftServer)` |
| | `START_LEVEL`, `END_LEVEL` | `(ServerLevel)` |
| `PlayerEvents` | `JOIN`, `LEAVE` | `(ServerPlayer)` |
| | `START_TICK`, `END_TICK` | `(Player)`: both sides; check `player.level().isClientSide()` |
| | `CLONE` | `(ServerPlayer original, ServerPlayer player, boolean died)`: the game made a new player object after death or the End; carry over what it doesn't (attachments kept through death already are) |
| | `RESPAWN` | `(ServerPlayer player, boolean fromEnd)`: back in the world as the new object |
| | `TRAVEL_TO_DIMENSION` | `EventResult (ServerPlayer, ResourceKey<Level> from, ResourceKey<Level> to)`: before any move to another dimension (portals, commands, other mods' teleporters); interrupt to stay |
| | `CHANGE_DIMENSION` | `(ServerPlayer, ResourceKey<Level> from, ResourceKey<Level> to)`: after arriving |
| | `PICKUP_ITEM` | `EventResult (ServerPlayer, ItemEntity)`: the item may be picked up and fits; interrupt to leave it on the ground |
| | `CRAFTED`, `SMELTED` | `(Player, ItemStack, Container)`: taken out of a crafting or furnace result slot |
| `LivingEvents` | `DAMAGE` | `EventResult (LivingEntity, DamageSource, float amount)`: before armor; interrupt and no damage is taken |
| | `DEATH` | `EventResult (LivingEntity, DamageSource)`: interrupt to keep it alive (give it some health back) |
| | `EQUIPMENT_CHANGE` | `(LivingEntity, EquipmentSlot, ItemStack from, ItemStack to)`: what it wears or holds changed; it may be taken off again |
| `EntityEvents` | `LOAD`, `UNLOAD` | `(Entity, ServerLevel)`: spawned or loaded with a chunk, players included; and removed or unloaded |
| `BlockEvents` | `BREAK` | `EventResult (ServerLevel, BlockPos, BlockState, ServerPlayer)`: interrupt to cancel |
| | `PLACE` | `EventResult (Level, BlockPos, BlockState, @Nullable Entity placer, ItemStack)`: before any block item places (players, dispensers, machines) or an enderman sets its block down; interrupt and nothing is placed, the item kept |
| | `PLACED` | `(Level, BlockPos, BlockState, @Nullable Entity placer)` |
| `InteractionEvents` | `USE_BLOCK` | `InteractionResult (Player, InteractionHand, BlockHitResult)`: before the game uses the block; `PASS` lets it go on, anything else is the result |
| | `USE_ITEM`, `USE_ENTITY` | `InteractionResult (Player, InteractionHand[, Entity target])`: the same, for the item in hand and for an entity |
| | `ATTACK_BLOCK` | `EventResult (Player, InteractionHand, BlockPos, Direction)`: interrupt to stop it |
| | `ATTACK_ENTITY` | `EventResult (Player, Entity target)`: interrupt to stop it |
| `RecipeEvents` | `CRAFT_RESULT` | `EventResultHolder<ItemStack> (@Nullable Player, RecipeHolder<?>, CraftingInput, ItemStack result)`: a crafting grid's or a crafter's result; interrupt with another stack, or `ItemStack.EMPTY` so nothing can be crafted |
| `CommandEvents` | `REGISTER` | `(CommandDispatcher<CommandSourceStack>, CommandBuildContext, Commands.CommandSelection)` |
| `AttachmentEvents` | `CHANGING`, `ADDED`, `CHANGED`, `REMOVED`, `START_TRACKING`, `STOP_TRACKING`, `RECEIVED` | See [Attachments](attachments.md). |

!!! info "Which versions have which"
    Interaction events, `ItemTooltipEvents.APPEND` and the phase bands: since **26.1.2-6** (and 26.2, 26.3), **2100.4.0.0** (1.21.1) and **2001.4.0.0** (1.20.1).
    `LivingEvents.DAMAGE` / `DEATH`, `EntityEvents.UNLOAD`: since **26.1.2-6** (and 26.2, 26.3); not yet on 1.21.1 and 1.20.1.
    `PlayerEvents.CLONE` / `RESPAWN` / `CHANGE_DIMENSION`: since **26.1.2-6** (and 26.2, 26.3), **8.0.0** on 1.21.1 and 1.20.1.
    `BlockEvents.PLACE`, `PlayerEvents.PICKUP_ITEM` / `TRAVEL_TO_DIMENSION` / `CRAFTED` / `SMELTED`, `LivingEvents.EQUIPMENT_CHANGE` and `RecipeEvents`: coming in **8.0.0** (on 26.1.2, 1.21.1 and 1.20.1). On 1.20.1, `CRAFT_RESULT` takes `(Player, Recipe<?>, CraftingContainer, ItemStack)`, as that version has no `RecipeHolder` or `CraftingInput`, and there's no crafter.

### Client (`api.event.v2.client`)

| Holder | Event | Listener |
|---|---|---|
| `ClientTickEvents` | `START`, `END` | `(Minecraft)` |
| `ClientPlayerEvents` | `JOIN` | `(LocalPlayer)` |
| | `LEAVE` | `(@Nullable LocalPlayer)` |
| `ClientGuiEvents` | `RENDER_HUD` | `(GuiGraphicsExtractor graphics, DeltaTracker delta)`: draws over the vanilla HUD every frame |
| `ItemTooltipEvents` | `MODIFY` | `(ItemStack, Item.TooltipContext, TooltipFlag, List<Component> lines)`: the finished lines, name included |
| | `APPEND` | `(ItemStack, Item.TooltipContext, @Nullable Player, TooltipFlag, List<Component> lines)`: after every other mod's lines; what you add comes last |
| `ClientCommandEvents` | `REGISTER` | `(CommandDispatcher<SharedSuggestionProvider>, CommandBuildContext)`. Reply with `ClientCommandEvents.sendFeedback(component)` or `sendError(component)`. |

### Elsewhere

- Config events (`ConfigEvents`): see [Configs](configs.md).
- Screen effect events (`ScreenEffectEvents`, in Canvas): see [Canvas Screen Effects](../canvas/screen-effects.md).
- Brewing recipes: `api.event.v1.server.RegisterBrewingRecipesEvent.EVENT.register(event -> event.getBuilder().addRecipe(input, ingredient, output))`, from the older `event.v1` API.

## Reference

| Type | Description |
|---|---|
| `EventInvoker<T>` | `register(listener)`, `register(EventPhase, listener)`, `unregister(listener)`, `hasListeners()`, `invoker()`, `type()`; static `lookup(Class)`, `create(Class)`, `create(Class, combiner)`. |
| `EventPhase` | `FIRST` (-2000), `EARLY` (-1000), `DEFAULT` (0), `LATE` (1000), `LAST` (2000); `of(name, order)`, `before()`, `after()`. |
| `EventResult` | `PASS`, `INTERRUPT`, `ALLOW` (interrupts as true), `DENY` (interrupts as false); `pass()`, `interrupt()`, `allow()`, `deny()`, `isInterrupt()`, `isPass()`, `getAsBoolean()`. |
| `EventResultHolder<T>` | `pass()`, `interrupt(value)`, `allow(value)`, `deny(value)`; `getValue()`, `getInterrupt()`, `getAllow()`, `getDeny()`, `ifInterrupt`, `ifAllow`, `ifDeny`, `map`, `flatMap`, `filter`, `result()`. |
