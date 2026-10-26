---
icon: material/paperclip
description: Your data on entities, block entities, stacks, levels and the server
---

# Attachments

Attach your own data to entities (players included), block entities, item stacks, levels and the server itself. It can be saved with them, synced to clients, and kept through death; players' saved data can be read and written while they're offline. Package: `net.ixdarklord.coolcatcore.api.attachment`.

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

## Levels and the server

The same attachments hold values for a whole level, or for the whole server: a world's progression, a team's unlocks.

```java
MyAttachments.STAGE.set(serverLevel, 3);                 // this level only, saved in its data folder
MyAttachments.STAGE.update(server, stage -> stage + 1);  // the whole server

int stage = MyAttachments.STAGE.getGlobal(level);         // the server's value, on either side
int here = MyAttachments.STAGE.get(minecraft.level);      // the level's, on the client
```

- **Saved** as saved data: a level's in its own data folder, the server's in the server's (on 1.21.1 and 1.20.1, the overworld's).
- **Synced** by the attachment's `SyncPolicy`, where the owner is the level or the server: `ALL` sends a level's values to the players in it and the server's to every player, `NONE` keeps them on the server (`SELF` sends nothing, as there's no owning player). Players get them in full when they join, respawn or change dimension.
- **On the client**, `get(level)` reads the level the player is in; `getGlobal(level)` the server's copy, which is forgotten when the player leaves.

## Offline players

A player's saved values can be read and changed whether or not they're online: an online player's through their entity, an offline one's from their data file, written at once.

```java
int madness = MyAttachments.MADNESS.getOffline(server, uuid);          // the default if they never played here
boolean written = MyAttachments.MADNESS.setOffline(server, uuid, 5);   // false if they never played here
MyAttachments.MADNESS.updateOffline(server, uuid, m -> m + 1);
MyAttachments.MADNESS.resetOffline(server, uuid);
```

Only persistent attachments (with a codec) can be used this way, on the server thread. No `AttachmentEvents` fire for an offline player's values; they reach the player when they next join.

!!! info "Since"
    Levels, the server and offline players: coming in **8.0.0**, on every version.

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
- **Levels and the server:** in saved data, `coolcatlib:attachments` and `coolcatlib:server_attachments` (on 1.21.1 and 1.20.1, `coolcatlib_attachments` and the overworld's `coolcatlib_server_attachments`).
- **Item stacks:** in a `<modid>:attachments` data component.
- **Block items:** use `keepOnDrop()`, which needs an immutable persistent value. The block's loot table must also copy the `<modid>:attachments` component (`copy_components`).

## Reference

| Type | Description |
|---|---|
| `AttachmentRegistry` | `create(modId)` (one per mod), `forMod(modId)`, `all()`. Builders: `builder(name, codec, default)` (persistent, immutable default), `mutableBuilder(name, codec, defaultSupplier)`, `transientBuilder(name, defaultSupplier)` (never saved), `container(name, ContainerLayout)`. `register()` during construction; `get(name)`, `getAttachments()`, `id(name)`. |
| `Attachment.Builder<T>` | `networkCodec(StreamCodec)`, `sync(SyncPolicy)`, `copyOnDeath()`, `keepOnDrop()`, `onChange(listener)`, `build()`. A synced transient attachment needs a network codec; a synced persistent one falls back to its `Codec`. |
| `Attachment<T>` | `get`, `set`, `update(UnaryOperator)`, `modify(Consumer)`, `has`, `reset` on an entity, block entity, stack, `Level` or `MinecraftServer`; `getGlobal(level)`; `getOffline`, `setOffline`, `updateOffline`, `resetOffline` (server, player UUID); `onChange(listener)`; `id()`, `name()`, `syncPolicy()`, `isPersistent()`, `isSynced()`; `byId(id)`. |
| `AttachmentHolder` | `of(Entity / BlockEntity / ItemStack / Level / MinecraftServer)`, `global(level)`: `get`, `set`, `update`, `modify`, `markDirty`, `has`, `reset`, `attachments()`, `owner()`, `isClientSide()`. |
| `SyncPolicy` | `NONE`, `SELF` (only the owner, for player data), `TRACKING` (players who can see it, not the owner), `ALL`; combine with `and`/`or`, or write your own `(owner, player) -> boolean`. |
| `Trackable` | Implement on a mutable value so it reports its own changes (like `SlotContainer`). |
| `api.event.v2.common.AttachmentEvents` | `CHANGING` (return `pass()`, `deny(x)` to cancel, or `allow(value)` to store another value), `ADDED`, `CHANGED`, `REMOVED`, `START_TRACKING`, `STOP_TRACKING`, `RECEIVED` (client); typed helpers `onChanging`, `onAdded`, `onChanged`, `onRemoved`, `onAnyChange` for one attachment. |
