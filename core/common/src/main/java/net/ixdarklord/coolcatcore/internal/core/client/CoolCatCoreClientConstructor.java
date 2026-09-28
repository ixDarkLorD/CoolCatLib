package net.ixdarklord.coolcatcore.internal.core.client;

import net.ixdarklord.coolcatcore.api.core.ClientModConstructor;
import net.ixdarklord.coolcatcore.internal.config.client.ClientConfigManager;

// CoolCat Core's own client entry point.
public final class CoolCatCoreClientConstructor implements ClientModConstructor {
    @Override
    public void onConstructMod() {
        ClientConfigManager.init();
    }
}
