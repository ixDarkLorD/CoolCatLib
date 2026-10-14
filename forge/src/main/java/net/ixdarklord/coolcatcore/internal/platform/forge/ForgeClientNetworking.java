package net.ixdarklord.coolcatcore.internal.platform.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

// Client-only: loaded only when a payload reaches a client.
final class ForgeClientNetworking {
    private ForgeClientNetworking() {}

    static Player player() {
        return Minecraft.getInstance().player;
    }
}
