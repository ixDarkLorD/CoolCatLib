---
icon: material/access-point-network
description: Payloads with their codecs derived for you
---

# Networking

Sending data between client and server with custom payloads. Package: `net.ixdarklord.coolcatcore.api.network`.

Register every payload during construction (`ModConstructor#onConstructMod()`), on **both** sides.

## Record payloads: no codec needed

If every component of a record payload is a type `PayloadCodecs` knows, CoolCatLib builds the `StreamCodec` for you:

```java
public record PingPayload(BlockPos pos, List<String> tags, Optional<Component> note) implements CustomPacketPayload {
    public static final Type<PingPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("mymod", "ping"));

    @Override
    public Type<PingPayload> type() { return TYPE; }
}

// onConstructMod():
Network.registerServerbound(PingPayload.class, (payload, context) -> {
    ServerPlayer sender = (ServerPlayer) context.getPlayer();
    // Runs on the main thread.
});

// On the client:
Network.sendToServer(new PingPayload(pos, List.of("a"), Optional.empty()));
```

The payload's `Type` is read from the record's single static `Type` field (or the one named `TYPE`). Registering a payload with a component type that has no codec throws right away, naming the component.

Types `PayloadCodecs` knows out of the box:
- primitives and their boxes, `String`, `byte[]`, `long[]`, `OptionalInt`, `UUID`;
- `Identifier`, `BlockPos`, `ChunkPos`, `GlobalPos`, `Direction`, `Vec3`, `Vector3f`, `Quaternionf`;
- `Component`, `ItemStack`, `CompoundTag`, `Tag`, `BlockState`, `Item`, `Block`, `EntityType`;
- built from their parts: enums, records, arrays, `Optional`, `List`/`Collection`, `Set`, `Map`, `Either` and `ResourceKey`.

Components can't be null; use `Optional`. Teach it other types with `PayloadCodecs.register(MyType.class, codec)`, or `registerFactory(...)` for generic types.

## With your own codec

```java
Network.registerClientbound(MyPayload.TYPE, MyPayload.STREAM_CODEC, (payload, context) -> {
    // Client only; runs on the main thread.
});
```

## Sending

```java
Network.sendToServer(payload);                        // client → server
Network.sendToPlayer(serverPlayer, payload);          // server → one player
Network.sendToPlayers(server.getPlayerList().getPlayers(), payload);

// Players without your mod (and GameTest mock players) have no channel for it; NeoForge throws if you send anyway.
if (Network.canPlayerReceive(serverPlayer, MyPayload.TYPE)) Network.sendToPlayer(serverPlayer, payload);
```

## Reference

| Type | Description |
|---|---|
| `Network` | Register: `registerServerbound(type, codec, receiver)`, `registerClientbound(type, codec, receiver)`, and the record forms `registerServerbound(Class, receiver)`, `registerServerbound(Type, Class, receiver)`, `registerClientbound(Class, receiver)`, `registerClientbound(Type, Class, receiver)`. Send: `sendToServer`, `sendToPlayer`, `sendToPlayers`. `canPlayerReceive(player, type)`. |
| `PayloadReceiver<T>` | `receive(T payload, PacketContext context)`, called on the main thread. |
| `PacketContext` | `getPlayer()` (the sender on the server, the local player on the client), `queue(Runnable)`, `getEnvironment()`. |
| `PayloadCodecs` | `register(Class<V>, StreamCodec)`, `registerFactory(Class<?> rawType, Factory)`, `forRecord(Class<R>)`, `get(Type)`, `has(Type)`. |
