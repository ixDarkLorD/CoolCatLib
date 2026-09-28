package net.ixdarklord.coolcatcore.internal.platform.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;

// Client-only: loaded only when a payload reaches a client or a client sends one.
final class ForgeClientNetworking {
    private ForgeClientNetworking() {}

    static Player player() {
        return Minecraft.getInstance().player;
    }

    static void sendToServer(CustomPacketPayload payload) {
        ForgeNetworking.send(payload, PacketDistributor.SERVER.noArg());
    }
}
