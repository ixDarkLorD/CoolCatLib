package net.ixdarklord.coolcatcanvas.internal.core.client;

import net.ixdarklord.coolcatcanvas.internal.client.camera.CameraShakeManager;
import net.ixdarklord.coolcatcanvas.internal.client.effect.ScreenEffectsClient;
import net.ixdarklord.coolcatcore.api.core.ClientModConstructor;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientTickEvents;

// CoolCatLib: Canvas's client entry point.
public final class CoolCatCanvasClientConstructor implements ClientModConstructor {
    @Override
    public void onConstructMod() {
        ScreenEffectsClient.init();
        ClientTickEvents.END.register(CameraShakeManager::tick);
    }
}
