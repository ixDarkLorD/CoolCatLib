package net.ixdarklord.coolcatcore.internal.platform.fabric;

import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.ixdarklord.coolcatcore.api.network.CustomPacketPayload;
import net.ixdarklord.coolcatcore.api.network.PacketContext;
import net.ixdarklord.coolcatcore.api.network.PayloadReceiver;
import net.ixdarklord.coolcatcore.api.network.codec.StreamCodec;
import net.ixdarklord.coolcatcore.api.platform.Env;
import net.ixdarklord.coolcatcore.api.platform.Platform;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.ixdarklord.coolcatcore.internal.platform.CommonServices;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

// Fabric API 0.92 (1.20.1) has no payload types: every payload travels as a custom packet on the channel named after
// its type's id, written with its codec. Receivers are handed the packet on the network thread, so the payload is read
// there and handled on the game's main thread.
//
// 1.20.1 has no configuration phase either: configuration payloads go out as login queries on their type's channel,
// and the client answers each once its receiver has run on the main thread (a boolean, then the reason when the client
// asks to be disconnected). The server holds the login until every query is answered.
final class FabricNetworking {
    private static final Map<ResourceLocation, StreamCodec<? super FriendlyByteBuf, ?>> SERVERBOUND = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, StreamCodec<? super FriendlyByteBuf, ?>> CLIENTBOUND = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, StreamCodec<? super FriendlyByteBuf, ?>> LOGIN = new ConcurrentHashMap<>();
    private static final List<Supplier<List<CustomPacketPayload>>> LOGIN_PAYLOADS = new CopyOnWriteArrayList<>();
    private static boolean loginHooked;

    private FabricNetworking() {}

    static <T extends CustomPacketPayload> void registerServerbound(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec, PayloadReceiver<T> receiver) {
        add(SERVERBOUND, type, codec);
        ServerPlayNetworking.registerGlobalReceiver(type.id(), (server, player, handler, buf, responseSender) -> {
            T payload = decode(type, codec, buf);
            server.execute(() -> receiver.receive(payload, new ServerContext(server, player)));
        });
    }

    static <T extends CustomPacketPayload> void registerClientbound(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec, PayloadReceiver<T> receiver) {
        add(CLIENTBOUND, type, codec);
        if (Platform.isClient()) FabricClientNetworking.registerReceiver(type, codec, receiver);
    }

    static <T extends CustomPacketPayload> void registerLoginClientbound(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec, CommonServices.ConfigurationReceiver<T> receiver) {
        add(LOGIN, type, codec);
        // A client without the channel answers "not understood", which is fine: it just doesn't take part.
        ServerLoginNetworking.registerGlobalReceiver(type.id(), (server, handler, understood, buf, synchronizer, responseSender) -> {
            if (understood && buf.isReadable() && buf.readBoolean()) handler.disconnect(buf.readComponent());
        });
        if (Platform.isClient()) FabricClientNetworking.registerLoginReceiver(type, codec, receiver);
    }

    static synchronized void addLoginPayloads(Supplier<List<CustomPacketPayload>> payloads) {
        LOGIN_PAYLOADS.add(payloads);
        if (loginHooked) return;
        loginHooked = true;
        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) -> {
            for (Supplier<List<CustomPacketPayload>> supplier : LOGIN_PAYLOADS) {
                for (CustomPacketPayload payload : supplier.get()) {
                    if (!LOGIN.containsKey(payload.type().id())) {
                        CoolCatCore.LOGGER.error("Can't send {} while a client logs in: it isn't registered as a configuration payload", payload.type().id());
                        continue;
                    }
                    sender.sendPacket(payload.type().id(), encode(LOGIN, payload));
                }
            }
        });
    }

    static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload.type().id(), encode(CLIENTBOUND, payload));
    }

    /** The payload written with the codec it was registered with, in a new buffer. */
    static FriendlyByteBuf encodeServerbound(CustomPacketPayload payload) {
        return encode(SERVERBOUND, payload);
    }

    @SuppressWarnings("unchecked")
    private static FriendlyByteBuf encode(Map<ResourceLocation, StreamCodec<? super FriendlyByteBuf, ?>> codecs, CustomPacketPayload payload) {
        ResourceLocation id = payload.type().id();
        StreamCodec<? super FriendlyByteBuf, CustomPacketPayload> codec = (StreamCodec<? super FriendlyByteBuf, CustomPacketPayload>) codecs.get(id);
        if (codec == null) throw new IllegalArgumentException("Payload " + id + " isn't registered for this direction");
        FriendlyByteBuf buf = PacketByteBufs.create();
        try {
            codec.encode(buf, payload);
        } catch (RuntimeException e) {
            buf.release();
            throw new EncoderException("Failed to encode payload " + id, e);
        }
        return buf;
    }

    static <T extends CustomPacketPayload> T decode(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec, FriendlyByteBuf buf) {
        try {
            return codec.decode(buf);
        } catch (RuntimeException e) {
            throw new DecoderException("Failed to decode payload " + type.id(), e);
        }
    }

    private static void add(Map<ResourceLocation, StreamCodec<? super FriendlyByteBuf, ?>> codecs, CustomPacketPayload.Type<?> type, StreamCodec<? super FriendlyByteBuf, ?> codec) {
        if (codecs.putIfAbsent(type.id(), codec) != null) {
            throw new IllegalArgumentException("Payload " + type.id() + " is already registered");
        }
    }

    private record ServerContext(MinecraftServer server, ServerPlayer player) implements PacketContext {
        @Override
        public Player getPlayer() {
            return this.player;
        }

        @Override
        public void queue(Runnable task) {
            this.server.execute(task);
        }

        @Override
        public Env getEnvironment() {
            return Env.SERVER;
        }
    }

    /** Written by the client in answer to a login payload. */
    static FriendlyByteBuf loginAnswer(@Nullable Component disconnectReason) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(disconnectReason != null);
        if (disconnectReason != null) buf.writeComponent(disconnectReason);
        return buf;
    }
}
