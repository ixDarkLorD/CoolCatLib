package net.ixdarklord.coolcatcore.internal.platform.fabric;

import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ixdarklord.coolcatcore.api.network.CustomPacketPayload;
import net.ixdarklord.coolcatcore.api.network.PacketContext;
import net.ixdarklord.coolcatcore.api.network.PayloadReceiver;
import net.ixdarklord.coolcatcore.api.network.codec.StreamCodec;
import net.ixdarklord.coolcatcore.api.platform.Env;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.ixdarklord.coolcatcore.internal.platform.CommonServices;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.concurrent.atomic.AtomicReference;

// Client-only: loaded only when the game is a client.
final class FabricClientNetworking {
    private FabricClientNetworking() {}

    static <T extends CustomPacketPayload> void registerReceiver(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec, PayloadReceiver<T> receiver) {
        ClientPlayNetworking.registerGlobalReceiver(type.id(), (client, handler, buf, responseSender) -> {
            T payload = FabricNetworking.decode(type, codec, buf);
            client.execute(() -> receiver.receive(payload, new PacketContext() {
                @Override
                public Player getPlayer() {
                    return client.player;
                }

                @Override
                public void queue(Runnable task) {
                    client.execute(task);
                }

                @Override
                public Env getEnvironment() {
                    return Env.CLIENT;
                }
            }));
        });
    }

    // Fabric hands login queries over on the network thread; receivers run on the game's, as on Forge. The answer goes
    // back once the receiver has run, and asks the server to end the connection if the receiver disconnected.
    static <T extends CustomPacketPayload> void registerLoginReceiver(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec, CommonServices.ConfigurationReceiver<T> receiver) {
        ClientLoginNetworking.registerGlobalReceiver(type.id(), (client, handler, buf, listenerAdder) -> {
            T payload = FabricNetworking.decode(type, codec, buf);
            return client.submit(() -> {
                AtomicReference<Component> disconnect = new AtomicReference<>();
                try {
                    receiver.receive(payload, disconnect::set);
                } catch (RuntimeException e) {
                    CoolCatCore.LOGGER.error("Failed to handle login payload {}", type.id(), e);
                }
                return FabricNetworking.loginAnswer(disconnect.get());
            });
        });
    }

    static void send(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload.type().id(), FabricNetworking.encodeServerbound(payload));
    }
}
