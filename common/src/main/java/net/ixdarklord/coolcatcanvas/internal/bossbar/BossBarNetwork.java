package net.ixdarklord.coolcatcanvas.internal.bossbar;

import net.ixdarklord.coolcatcanvas.internal.client.bossbar.BossBarClient;
import net.ixdarklord.coolcatcore.api.network.Network;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;

public final class BossBarNetwork {
    private BossBarNetwork() {}

    public static void init() {
        Network.registerClientbound(BossBarStylePayload.TYPE, BossBarStylePayload.STREAM_CODEC, (payload, context) -> context.queue(() -> BossBarClient.onStyle(payload)));
    }

    /** Tells a player who sees a bar what style it is in (if it has one, and they can be told). */
    public static void send(ServerBossEvent event, ServerPlayer player) {
        StyledBossEvent styled = (StyledBossEvent) event;
        if (styled.coolcatlib$style() == null || !Network.canPlayerReceive(player, BossBarStylePayload.TYPE)) return;
        Network.sendToPlayer(player, new BossBarStylePayload(event.getId(), styled.coolcatlib$style(), styled.coolcatlib$entityId()));
    }
}
