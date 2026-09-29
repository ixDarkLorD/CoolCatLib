package net.ixdarklord.coolcatcore.api.network;

import net.ixdarklord.coolcatcore.internal.platform.ClientServices;
import net.ixdarklord.coolcatcore.internal.platform.CommonServices;
import net.minecraft.network.FriendlyByteBuf;
import net.ixdarklord.coolcatcore.api.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Play-phase payloads between client and server.
 * <p>
 * Register every payload while the mod initialises, on both sides: a dedicated server still needs to know the payloads
 * it sends. On Forge they all travel through CoolCatLib: Core's channel, told apart by their type's id; on Fabric each
 * type's id is its own channel.
 * <p>
 * A record payload can skip its {@link StreamCodec}: pass the record class and each component is encoded with the
 * codec {@link PayloadCodecs} has for its type.
 * <pre>{@code
 * public record PingPayload(BlockPos pos, List<String> tags, Optional<Component> note) implements CustomPacketPayload {
 *     public static final Type<PingPayload> TYPE = new Type<>(new ResourceLocation("mymod", "ping"));
 *
 *     public Type<PingPayload> type() { return TYPE; }
 * }
 *
 * Network.registerServerbound(PingPayload.class, (payload, context) -> ...);
 * }</pre>
 */
public final class Network {
    private Network() {}

    /**
     * A payload the client sends and the server receives.
     */
    public static <T extends CustomPacketPayload> void registerServerbound(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec, PayloadReceiver<T> receiver) {
        CommonServices.get().registerServerbound(type, codec, receiver);
    }

    /**
     * A payload the server sends and the client receives. The receiver only runs on clients.
     */
    public static <T extends CustomPacketPayload> void registerClientbound(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec, PayloadReceiver<T> receiver) {
        CommonServices.get().registerClientbound(type, codec, receiver);
    }

    /**
     * A record payload the client sends and the server receives, with its codec derived from its components and its
     * type read from its static {@link CustomPacketPayload.Type} field.
     *
     * @throws IllegalArgumentException if a component's type has no codec in {@link PayloadCodecs}, or the record has no
     *                                  (or more than one) static {@code Type} field
     */
    public static <T extends Record & CustomPacketPayload> void registerServerbound(Class<T> payloadClass, PayloadReceiver<T> receiver) {
        registerServerbound(typeOf(payloadClass), payloadClass, receiver);
    }

    /**
     * A record payload the client sends and the server receives, with its codec derived from its components.
     *
     * @throws IllegalArgumentException if a component's type has no codec in {@link PayloadCodecs}
     */
    public static <T extends Record & CustomPacketPayload> void registerServerbound(CustomPacketPayload.Type<T> type, Class<T> payloadClass, PayloadReceiver<T> receiver) {
        registerServerbound(type, PayloadCodecs.forRecord(payloadClass), receiver);
    }

    /**
     * A record payload the server sends and the client receives, with its codec derived from its components and its
     * type read from its static {@link CustomPacketPayload.Type} field. The receiver only runs on clients.
     *
     * @throws IllegalArgumentException if a component's type has no codec in {@link PayloadCodecs}, or the record has no
     *                                  (or more than one) static {@code Type} field
     */
    public static <T extends Record & CustomPacketPayload> void registerClientbound(Class<T> payloadClass, PayloadReceiver<T> receiver) {
        registerClientbound(typeOf(payloadClass), payloadClass, receiver);
    }

    /**
     * A record payload the server sends and the client receives, with its codec derived from its components.
     * The receiver only runs on clients.
     *
     * @throws IllegalArgumentException if a component's type has no codec in {@link PayloadCodecs}
     */
    public static <T extends Record & CustomPacketPayload> void registerClientbound(CustomPacketPayload.Type<T> type, Class<T> payloadClass, PayloadReceiver<T> receiver) {
        registerClientbound(type, PayloadCodecs.forRecord(payloadClass), receiver);
    }

    /** Client only. */
    public static void sendToServer(CustomPacketPayload payload) {
        ClientServices.get().sendToServer(payload);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        CommonServices.get().sendToPlayer(player, payload);
    }

    public static void sendToPlayers(Iterable<ServerPlayer> players, CustomPacketPayload payload) {
        for (ServerPlayer player : players) sendToPlayer(player, payload);
    }

    /**
     * Whether this player's connection accepts the payload: false for clients without the mod that registered it, and
     * for connections that never negotiated payloads (e.g. GameTest mock players). Check first when that can happen.
     */
    public static boolean canPlayerReceive(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return CommonServices.get().canPlayerReceive(player, type);
    }

    // The payload's static Type field: the only one, or the one named TYPE.
    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(Class<T> payloadClass) {
        List<Field> fields = new ArrayList<>();
        for (Field field : payloadClass.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == CustomPacketPayload.Type.class) fields.add(field);
        }
        Field field = fields.size() == 1 ? fields.get(0) : fields.stream().filter(f -> f.getName().equals("TYPE")).findFirst().orElseThrow(() ->
                new IllegalArgumentException(payloadClass.getName() + " needs one static CustomPacketPayload.Type field (or one named TYPE), or pass its type explicitly"));
        try {
            field.trySetAccessible();
            Object type = field.get(null);
            if (type == null) throw new IllegalArgumentException(payloadClass.getName() + "." + field.getName() + " is null");
            return (CustomPacketPayload.Type<T>) type;
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Can't read " + payloadClass.getName() + "." + field.getName() + "; make it public or pass the type explicitly", e);
        }
    }
}
