package net.ixdarklord.coolcatcore.internal.core.client;

import net.ixdarklord.coolcatcore.api.core.ClientModConstructor;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientPlayerEvents;
import net.ixdarklord.coolcatcore.internal.attachment.ServerAttachments;
import net.ixdarklord.coolcatcore.internal.config.client.ClientConfigManager;

// CoolCatLib's own client entry point.
public final class CoolCatCoreClientConstructor implements ClientModConstructor {
    @Override
    public void onConstructMod() {
        ClientConfigManager.init();
        // The server's attachment values belong to the server the client left.
        ClientPlayerEvents.LEAVE.register(player -> ServerAttachments.clearClient());
    }
}
