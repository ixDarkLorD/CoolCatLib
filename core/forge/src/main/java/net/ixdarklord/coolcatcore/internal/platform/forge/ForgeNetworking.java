package net.ixdarklord.coolcatcore.internal.platform.forge;

import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.ixdarklord.coolcatcore.api.network.CustomPacketPayload;
import net.ixdarklord.coolcatcore.api.network.PacketContext;
import net.ixdarklord.coolcatcore.api.network.PayloadReceiver;
import net.ixdarklord.coolcatcore.api.network.codec.StreamCodec;
import net.ixdarklord.coolcatcore.api.platform.Env;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.ixdarklord.coolcatcore.internal.platform.CommonServices;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.HandshakeHandler;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

// Forge 47 (1.20.1) has no payload types: every payload travels through CoolCatLib: Core's one SimpleChannel, as a
// message carrying its type's id and then what its codec wrote. Ids rather than indices tell payloads apart, since mods
// are constructed in parallel and register their payloads in no fixed order. The channel is required on both ends,
// like payloads on newer versions; receivers run on the main thread. A payload the receiving side doesn't know is skipped.
//
// 1.20.1 has no configuration phase either: configuration payloads are sent as login packets of the channel, and the
// client answers each once its receiver has run on the main thread. Forge holds the login until every answer is in.
public final class ForgeNetworking {
    private static final String NETWORK_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(CoolCatCore.rl("network"),
            () -> NETWORK_VERSION, NETWORK_VERSION::equals, NETWORK_VERSION::equals);

    private static final Map<ResourceLocation, PlayEntry<?>> SERVERBOUND = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, PlayEntry<?>> CLIENTBOUND = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, LoginEntry<?>> LOGIN = new ConcurrentHashMap<>();
    // Sent to each client as its login starts.
    private static final List<Supplier<List<CustomPacketPayload>>> LOGIN_PAYLOADS = new CopyOnWriteArrayList<>();

    static {
        BiConsumer<ServerboundMessage, Supplier<NetworkEvent.Context>> serverbound = ForgeNetworking::handleServerbound;
        CHANNEL.messageBuilder(ServerboundMessage.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((message, buf) -> encode(message.entry, message.payload, buf))
                .decoder(buf -> new ServerboundMessage(decodePlay(SERVERBOUND, buf)))
                .consumerNetworkThread(serverbound)
                .add();
        BiConsumer<ClientboundMessage, Supplier<NetworkEvent.Context>> clientbound = ForgeNetworking::handleClientbound;
        CHANNEL.messageBuilder(ClientboundMessage.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((message, buf) -> encode(message.entry, message.payload, buf))
                .decoder(buf -> new ClientboundMessage(decodePlay(CLIENTBOUND, buf)))
                .consumerNetworkThread(clientbound)
                .add();
        BiConsumer<LoginQuery, Supplier<NetworkEvent.Context>> loginQuery = ForgeNetworking::handleLoginQuery;
        CHANNEL.messageBuilder(LoginQuery.class, 2, NetworkDirection.LOGIN_TO_CLIENT)
                .loginIndex(LoginQuery::getAsInt, LoginQuery::setLoginIndex)
                .encoder((message, buf) -> encode(message.entry, message.payload, buf))
                .decoder(ForgeNetworking::decodeLoginQuery)
                .buildLoginPacketList(ForgeNetworking::loginQueries)
                .consumerNetworkThread(loginQuery)
                .add();
        CHANNEL.messageBuilder(LoginAnswer.class, 3, NetworkDirection.LOGIN_TO_SERVER)
                .loginIndex(LoginAnswer::getAsInt, LoginAnswer::setLoginIndex)
                .encoder((message, buf) -> {})
                .decoder(buf -> new LoginAnswer())
                .consumerNetworkThread(HandshakeHandler.<LoginAnswer>indexFirst((handshake, message, context) -> context.get().setPacketHandled(true)))
                .add();
    }

    private ForgeNetworking() {}

    /** Creates the channel; Forge only takes new channels while mods load. */
    public static void init() {
    }

    public static <T extends CustomPacketPayload> void registerServerbound(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec, PayloadReceiver<T> receiver) {
        add(SERVERBOUND, type, new PlayEntry<>(type, codec, receiver));
    }

    public static <T extends CustomPacketPayload> void registerClientbound(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec, PayloadReceiver<T> receiver) {
        add(CLIENTBOUND, type, new PlayEntry<>(type, codec, receiver));
    }

    public static <T extends CustomPacketPayload> void registerLoginClientbound(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec, CommonServices.ConfigurationReceiver<T> receiver) {
        add(LOGIN, type, new LoginEntry<>(type, codec, receiver));
    }

    public static void addLoginPayloads(Supplier<List<CustomPacketPayload>> payloads) {
        LOGIN_PAYLOADS.add(payloads);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ClientboundMessage(entry(CLIENTBOUND, payload), payload));
    }

    /** Client only. */
    public static void sendToServer(CustomPacketPayload payload) {
        CHANNEL.sendToServer(new ServerboundMessage(entry(SERVERBOUND, payload), payload));
    }

    // --- Play ---

    private static void handleServerbound(ServerboundMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.setPacketHandled(true);
        ServerPlayer sender = context.getSender();
        if (message.payload == null || sender == null) return;
        context.enqueueWork(() -> receive(message.entry, message.payload, new Context(context, sender)));
    }

    private static void handleClientbound(ClientboundMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.setPacketHandled(true);
        if (message.payload == null) return;
        context.enqueueWork(() -> receive(message.entry, message.payload, new Context(context, null)));
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> void receive(PlayEntry<T> entry, CustomPacketPayload payload, PacketContext context) {
        entry.receiver.receive((T) payload, context);
    }

    // The payload the id names, or null (having skipped its bytes) when this side doesn't know it.
    private static @Nullable Decoded decodePlay(Map<ResourceLocation, PlayEntry<?>> entries, FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        PlayEntry<?> entry = entries.get(id);
        if (entry == null) {
            CoolCatCore.LOGGER.warn("Skipping payload {}, which isn't registered on this side", id);
            buf.skipBytes(buf.readableBytes());
            return null;
        }
        return new Decoded(entry, decode(id, entry.codec, buf));
    }

    private record Decoded(PlayEntry<?> entry, CustomPacketPayload payload) {}

    // --- Login ---

    private static List<Pair<String, LoginQuery>> loginQueries(boolean isLocal) {
        List<Pair<String, LoginQuery>> queries = new ArrayList<>();
        for (Supplier<List<CustomPacketPayload>> supplier : LOGIN_PAYLOADS) {
            for (CustomPacketPayload payload : supplier.get()) {
                LoginEntry<?> entry = LOGIN.get(payload.type().id());
                if (entry == null) {
                    CoolCatCore.LOGGER.error("Can't send {} while a client logs in: it isn't registered as a configuration payload", payload.type().id());
                    continue;
                }
                queries.add(Pair.of(payload.type().id().toString(), new LoginQuery(entry, payload)));
            }
        }
        return queries;
    }

    private static LoginQuery decodeLoginQuery(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        LoginEntry<?> entry = LOGIN.get(id);
        if (entry == null) {
            CoolCatCore.LOGGER.warn("Skipping login payload {}, which isn't registered on this side", id);
            buf.skipBytes(buf.readableBytes());
            return new LoginQuery(null, null);
        }
        return new LoginQuery(entry, decode(id, entry.codec, buf));
    }

    // On the client, during login: the receiver runs on the main thread, then the answer goes back (or the connection
    // ends, when the receiver asks for that).
    private static void handleLoginQuery(LoginQuery query, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.setPacketHandled(true);
        if (query.entry == null || query.payload == null) {
            CHANNEL.reply(new LoginAnswer(), context);
            return;
        }
        context.enqueueWork(() -> {
            Component[] disconnect = new Component[1];
            try {
                receiveLogin(query.entry, query.payload, reason -> disconnect[0] = reason);
            } catch (RuntimeException e) {
                CoolCatCore.LOGGER.error("Failed to handle login payload {}", query.payload.type().id(), e);
            }
            if (disconnect[0] != null) context.getNetworkManager().disconnect(disconnect[0]);
            else CHANNEL.reply(new LoginAnswer(), context);
        });
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> void receiveLogin(LoginEntry<T> entry, CustomPacketPayload payload, java.util.function.Consumer<Component> disconnect) {
        entry.receiver.receive((T) payload, disconnect);
    }

    // --- Codecs ---

    @SuppressWarnings("unchecked")
    private static void encode(@Nullable Entry entry, @Nullable CustomPacketPayload payload, FriendlyByteBuf buf) {
        if (entry == null || payload == null) throw new EncoderException("Can't send an unknown payload");
        ResourceLocation id = payload.type().id();
        buf.writeResourceLocation(id);
        try {
            ((StreamCodec<? super FriendlyByteBuf, CustomPacketPayload>) entry.codec()).encode(buf, payload);
        } catch (RuntimeException e) {
            throw new EncoderException("Failed to encode payload " + id, e);
        }
    }

    private static CustomPacketPayload decode(ResourceLocation id, StreamCodec<? super FriendlyByteBuf, ? extends CustomPacketPayload> codec, FriendlyByteBuf buf) {
        try {
            return codec.decode(buf);
        } catch (RuntimeException e) {
            throw new DecoderException("Failed to decode payload " + id, e);
        }
    }

    private static <E extends Entry> E entry(Map<ResourceLocation, E> entries, CustomPacketPayload payload) {
        E entry = entries.get(payload.type().id());
        if (entry == null) throw new IllegalArgumentException("Payload " + payload.type().id() + " isn't registered for this direction");
        return entry;
    }

    private static <E extends Entry> void add(Map<ResourceLocation, E> entries, CustomPacketPayload.Type<?> type, E entry) {
        if (entries.putIfAbsent(type.id(), entry) != null) {
            throw new IllegalArgumentException("Payload " + type.id() + " is already registered");
        }
    }

    private interface Entry {
        StreamCodec<? super FriendlyByteBuf, ? extends CustomPacketPayload> codec();
    }

    private record PlayEntry<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec, PayloadReceiver<T> receiver) implements Entry {}

    private record LoginEntry<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec, CommonServices.ConfigurationReceiver<T> receiver) implements Entry {}

    // --- Messages (public: Forge's channel builds them reflectively in places) ---

    public static final class ServerboundMessage {
        private final @Nullable PlayEntry<?> entry;
        private final @Nullable CustomPacketPayload payload;

        private ServerboundMessage(@Nullable PlayEntry<?> entry, @Nullable CustomPacketPayload payload) {
            this.entry = entry;
            this.payload = payload;
        }

        private ServerboundMessage(@Nullable Decoded decoded) {
            this(decoded != null ? decoded.entry : null, decoded != null ? decoded.payload : null);
        }
    }

    public static final class ClientboundMessage {
        private final @Nullable PlayEntry<?> entry;
        private final @Nullable CustomPacketPayload payload;

        private ClientboundMessage(@Nullable PlayEntry<?> entry, @Nullable CustomPacketPayload payload) {
            this.entry = entry;
            this.payload = payload;
        }

        private ClientboundMessage(@Nullable Decoded decoded) {
            this(decoded != null ? decoded.entry : null, decoded != null ? decoded.payload : null);
        }
    }

    public static final class LoginQuery implements IntSupplier {
        private final @Nullable LoginEntry<?> entry;
        private final @Nullable CustomPacketPayload payload;
        private int loginIndex;

        public LoginQuery() {
            this(null, null);
        }

        private LoginQuery(@Nullable LoginEntry<?> entry, @Nullable CustomPacketPayload payload) {
            this.entry = entry;
            this.payload = payload;
        }

        @Override
        public int getAsInt() {
            return this.loginIndex;
        }

        private void setLoginIndex(int loginIndex) {
            this.loginIndex = loginIndex;
        }
    }

    public static final class LoginAnswer implements IntSupplier {
        private int loginIndex;

        public LoginAnswer() {}

        @Override
        public int getAsInt() {
            return this.loginIndex;
        }

        private void setLoginIndex(int loginIndex) {
            this.loginIndex = loginIndex;
        }
    }

    private record Context(NetworkEvent.Context context, @Nullable ServerPlayer sender) implements PacketContext {
        @Override
        public Player getPlayer() {
            return this.sender != null ? this.sender : ForgeClientNetworking.player();
        }

        @Override
        public void queue(Runnable task) {
            this.context.enqueueWork(task);
        }

        @Override
        public Env getEnvironment() {
            return this.context.getDirection().getReceptionSide().isClient() ? Env.CLIENT : Env.SERVER;
        }
    }
}
