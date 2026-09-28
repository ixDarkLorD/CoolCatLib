package net.ixdarklord.coolcatcore.internal.platform.fabric;

import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ixdarklord.coolcatcore.api.network.PacketContext;
import net.ixdarklord.coolcatcore.api.network.PayloadReceiver;
import net.ixdarklord.coolcatcore.api.platform.Env;
import net.ixdarklord.coolcatcore.internal.platform.CommonServices;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

// Client-only: loaded only when the game is a client.
final class FabricClientNetworking {
    private FabricClientNetworking() {}

    static <T extends CustomPacketPayload> void registerReceiver(CustomPacketPayload.Type<T> type, PayloadReceiver<T> receiver) {
        ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> receiver.receive(payload, new PacketContext() {
            @Override
            public Player getPlayer() {
                return context.player();
            }

            @Override
            public void queue(Runnable task) {
                context.client().execute(task);
            }

            @Override
            public Env getEnvironment() {
                return Env.CLIENT;
            }
        }));
    }

    static <T extends CustomPacketPayload> void registerConfigurationReceiver(CustomPacketPayload.Type<T> type, CommonServices.ConfigurationReceiver<T> receiver) {
        // Fabric hands configuration payloads over on the network thread; receivers run on the game's, as on NeoForge.
        ClientConfigurationNetworking.registerGlobalReceiver(type, (payload, context) ->
                context.client().execute(() -> receiver.receive(payload, context.responseSender()::disconnect)));
    }

    static void send(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }
}
