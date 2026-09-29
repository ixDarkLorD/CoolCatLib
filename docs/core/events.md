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

// Events can also be found by their listener type.
EventInvoker.lookup(ServerTickEvents.End.class).register(server -> { });
```

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
| `EntityEvents` | `LOAD` | `(Entity, ServerLevel)`: spawned or loaded with a chunk, players included |
| `BlockEvents` | `BREAK` | `EventResult (ServerLevel, BlockPos, BlockState, ServerPlayer)`: interrupt to cancel |
| | `PLACED` | `(Level, BlockPos, BlockState, @Nullable Entity placer)` |
| `CommandEvents` | `REGISTER` | `(CommandDispatcher<CommandSourceStack>, CommandBuildContext, Commands.CommandSelection)` |
| `AttachmentEvents` | `CHANGING`, `ADDED`, `CHANGED`, `REMOVED`, `START_TRACKING`, `STOP_TRACKING`, `RECEIVED` | See [Attachments](attachments.md). |

### Client (`api.event.v2.client`)

| Holder | Event | Listener |
|---|---|---|
| `ClientTickEvents` | `START`, `END` | `(Minecraft)` |
| `ClientPlayerEvents` | `JOIN` | `(LocalPlayer)` |
| | `LEAVE` | `(@Nullable LocalPlayer)` |
| `ClientGuiEvents` | `RENDER_HUD` | `(GuiGraphicsExtractor graphics, DeltaTracker delta)`: draws over the vanilla HUD every frame |
| `ItemTooltipEvents` | `MODIFY` | `(ItemStack, Item.TooltipContext, TooltipFlag, List<Component> lines)`: the finished lines, name included |
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
