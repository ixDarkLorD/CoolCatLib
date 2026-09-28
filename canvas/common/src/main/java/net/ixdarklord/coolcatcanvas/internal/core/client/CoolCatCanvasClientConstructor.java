package net.ixdarklord.coolcatcanvas.internal.core.client;

import net.ixdarklord.coolcatcanvas.internal.client.effect.ScreenEffectsClient;
import net.ixdarklord.coolcatcore.api.core.ClientModConstructor;

// CoolCat Canvas's client entry point.
public final class CoolCatCanvasClientConstructor implements ClientModConstructor {
    @Override
    public void onConstructMod() {
        ScreenEffectsClient.init();
    }
}
