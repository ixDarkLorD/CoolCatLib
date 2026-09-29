# Attachments

Attach your own data to entities (players included), block entities and item stacks. It can be saved with them, synced to clients, and kept through death. Package: `net.ixdarklord.coolcatcore.api.attachment`.

## Declaring attachments

Each mod has one `AttachmentRegistry`:

```java
public final class MyAttachments {
    public static final AttachmentRegistry ATTACHMENTS = AttachmentRegistry.create(MyMod.MOD_ID);

    // Saved, synced to the owning player, and kept when the player dies.
    public static final Attachment<Integer> MADNESS = ATTACHMENTS.builder("madness", Codec.INT, 0)
            .sync(SyncPolicy.SELF).copyOnDeath().build();

    // A mutable value (new instance per holder), saved with the holder.
    public static final Attachment<PlayerStats> STATS = ATTACHMENTS.mutableBuilder("stats", PlayerStats.CODEC, PlayerStats::new)
            .networkCodec(PlayerStats.STREAM_CODEC).sync(SyncPolicy.SELF).build();

    // Never saved; synced with its own network codec.
    public static final Attachment<Integer> CALM_TICKS = ATTACHMENTS.transientBuilder("calm_ticks", () -> 0)
            .networkCodec(ByteBufCodecs.VAR_INT).sync(SyncPolicy.SELF).build();

    // A 9-slot inventory.
    public static final Attachment<SlotContainer> SATCHEL = ATTACHMENTS.container("satchel", ContainerLayout.of(9))
            .sync(SyncPolicy.SELF).copyOnDeath().build();

    public static void init() { ATTACHMENTS.register(); }   // from onConstructMod()
}
```

## Using them

```java
int madness = MyAttachments.MADNESS.get(player);
MyAttachments.MADNESS.set(player, 10);
MyAttachments.MADNESS.update(player, m -> Math.min(m + 1, 100));
MyAttachments.STATS.modify(player, stats -> stats.kills++);   // edit a mutable value in place: saved and synced
boolean has = MyAttachments.MADNESS.has(blockEntity);
MyAttachments.MADNESS.reset(entity);
```

The same calls work on `Entity`, `BlockEntity` and `ItemStack` (stacks have no `modify`, and only immutable, persistent values). Use `AttachmentHolder.of(...)` to treat all three alike.

!!! warning
    Change a mutable value only through `modify(...)` (or call `markDirty` on its holder). An in-place edit made any other way is neither saved nor synced.

## Reacting to changes

```java
// Clamp, veto or replace changes before they happen:
AttachmentEvents.onChanging(MyAttachments.MADNESS, (holder, oldValue, newValue) ->
        newValue != null && newValue > 100 ? EventResultHolder.allow(100) : EventResultHolder.pass());

// After any change (in-place ones included):
MyAttachments.MADNESS.onChange((holder, oldValue, newValue) -> { });

// On the client, after synced values arrive:
AttachmentEvents.RECEIVED.register((holder, attachments) -> { });
```

## Where values are stored

- **Entities and block entities:** in a `<modid>:attachments` tag in their NBT.
- **Item stacks:** in a `<modid>:attachments` data component.
- **Block items:** use `keepOnDrop()`, which needs an immutable persistent value. The block's loot table must also copy the `<modid>:attachments` component (`copy_components`).

## Reference

| Type | Description |
|---|---|
| `AttachmentRegistry` | `create(modId)` (one per mod), `forMod(modId)`, `all()`. Builders: `builder(name, codec, default)` (persistent, immutable default), `mutableBuilder(name, codec, defaultSupplier)`, `transientBuilder(name, defaultSupplier)` (never saved), `container(name, ContainerLayout)`. `register()` during construction; `get(name)`, `getAttachments()`, `id(name)`. |
| `Attachment.Builder<T>` | `networkCodec(StreamCodec)`, `sync(SyncPolicy)`, `copyOnDeath()`, `keepOnDrop()`, `onChange(listener)`, `build()`. A synced transient attachment needs a network codec; a synced persistent one falls back to its `Codec`. |
| `Attachment<T>` | `get`, `set`, `update(UnaryOperator)`, `modify(Consumer)`, `has`, `reset` on an entity, block entity or stack; `onChange(listener)`; `id()`, `name()`, `syncPolicy()`, `isPersistent()`, `isSynced()`; `byId(id)`. |
| `AttachmentHolder` | `of(Entity / BlockEntity / ItemStack)`: `get`, `set`, `update`, `modify`, `markDirty`, `has`, `reset`, `attachments()`, `owner()`, `isClientSide()`. |
| `SyncPolicy` | `NONE`, `SELF` (only the owner, for player data), `TRACKING` (players who can see it, not the owner), `ALL`; combine with `and`/`or`, or write your own `(owner, player) -> boolean`. |
| `Trackable` | Implement on a mutable value so it reports its own changes (like `SlotContainer`). |
| `api.event.v2.common.AttachmentEvents` | `CHANGING` (return `pass()`, `deny(x)` to cancel, or `allow(value)` to store another value), `ADDED`, `CHANGED`, `REMOVED`, `START_TRACKING`, `STOP_TRACKING`, `RECEIVED` (client); typed helpers `onChanging`, `onAdded`, `onChanged`, `onRemoved`, `onAnyChange` for one attachment. |
