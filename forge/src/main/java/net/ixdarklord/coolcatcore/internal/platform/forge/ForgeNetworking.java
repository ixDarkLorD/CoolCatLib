package net.ixdarklord.coolcatcore.internal.platform.forge;

import net.ixdarklord.coolcatcore.api.network.PacketContext;
import net.ixdarklord.coolcatcore.api.network.PayloadReceiver;
import net.ixdarklord.coolcatcore.api.platform.Env;
import net.ixdarklord.coolcatcore.internal.platform.CommonServices;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.payload.PayloadConnection;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

// Every payload gets a Forge payload channel of its own, named after its type and built as it's registered: Forge
// channels can't take payloads once built, and payloads are registered one at a time while mods are constructed
// (Forge locks its network registry only once loading completes). Channels are required on both ends, like NeoForge's
// payloads, and receivers run on the main thread.
public final class ForgeNetworking {
    private static final int NETWORK_VERSION = 1;
    private static final Map<ResourceLocation, Channel<CustomPacketPayload>> CHANNELS = new ConcurrentHashMap<>();

    private ForgeNetworking() {}

    @SuppressWarnings("unchecked")
    public static <T extends CustomPacketPayload> void registerPlay(CustomPacketPayload.Type<T> type, PacketFlow flow, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PayloadReceiver<T> receiver) {
        StreamCodec<RegistryFriendlyByteBuf, T> exact = (StreamCodec<RegistryFriendlyByteBuf, T>) codec;
        register(type, channel -> channel.play().flow(flow).add(type, exact, onMainThread((payload, context) -> receiver.receive(payload, new Context(context)))).build());
    }

    @SuppressWarnings("unchecked")
    public static <T extends CustomPacketPayload> void registerConfigurationClientbound(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec, CommonServices.ConfigurationReceiver<T> receiver) {
        StreamCodec<FriendlyByteBuf, T> exact = (StreamCodec<FriendlyByteBuf, T>) codec;
        register(type, channel -> channel.configuration().clientbound().add(type, exact, onMainThread((payload, context) -> {
            Connection connection = context.getConnection();
            receiver.receive(payload, connection::disconnect);
        })).build());
    }

    private static void register(CustomPacketPayload.Type<?> type, Function<PayloadConnection<CustomPacketPayload>, Channel<CustomPacketPayload>> builder) {
        ResourceLocation id = type.id();
        if (CHANNELS.containsKey(id)) throw new IllegalStateException("Payload " + id + " is already registered");
        Channel<CustomPacketPayload> channel = builder.apply(ChannelBuilder.named(id).networkProtocolVersion(NETWORK_VERSION).payloadChannel());
        CHANNELS.put(id, channel);
    }

    /** The channel a payload travels on. */
    public static Channel<CustomPacketPayload> channel(CustomPacketPayload payload) {
        Channel<CustomPacketPayload> channel = CHANNELS.get(payload.type().id());
        if (channel == null) throw new IllegalArgumentException("Payload " + payload.type().id() + " isn't registered");
        return channel;
    }

    public static void send(CustomPacketPayload payload, PacketDistributor.PacketTarget target) {
        channel(payload).send(payload, target);
    }

    public static void send(CustomPacketPayload payload, Connection connection) {
        channel(payload).send(payload, connection);
    }

    /** Whether the other end of the connection knows the payload. */
    public static boolean canSend(CustomPacketPayload payload, Connection connection) {
        return canSend(payload.type(), connection);
    }

    /** Whether the other end of the connection knows payloads of this type. */
    public static boolean canSend(CustomPacketPayload.Type<?> type, Connection connection) {
        Channel<CustomPacketPayload> channel = CHANNELS.get(type.id());
        return channel != null && channel.isRemotePresent(connection);
    }

    // Forge hands payloads over on the network thread.
    private static <T> BiConsumer<T, CustomPayloadEvent.Context> onMainThread(BiConsumer<T, CustomPayloadEvent.Context> handler) {
        return (payload, context) -> {
            context.enqueueWork(() -> handler.accept(payload, context));
            context.setPacketHandled(true);
        };
    }

    private record Context(CustomPayloadEvent.Context context) implements PacketContext {
        @Override
        public Player getPlayer() {
            return this.context.isClientSide() ? ForgeClientNetworking.player() : this.context.getSender();
        }

        @Override
        public void queue(Runnable task) {
            this.context.enqueueWork(task);
        }

        @Override
        public Env getEnvironment() {
            return this.context.isClientSide() ? Env.CLIENT : Env.SERVER;
        }
    }
}
