package net.ixdarklord.coolcatcanvas.internal.core.client;

import net.ixdarklord.coolcatcanvas.internal.client.camera.CameraShakeManager;
import net.ixdarklord.coolcatcanvas.internal.client.effect.ScreenEffectsClient;
import net.ixdarklord.coolcatcanvas.internal.client.vfx.RiftManager;
import net.ixdarklord.coolcatcanvas.internal.client.vfx.VfxManager;
import net.ixdarklord.coolcatcore.api.core.ClientModConstructor;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientTickEvents;
import net.ixdarklord.coolcatcanvas.internal.client.sound.SoundFilterManager;

// The client entry point of CoolCatLib's visuals (the coolcatcanvas package).
public final class CoolCatCanvasClientConstructor implements ClientModConstructor {
    @Override
    public void onConstructMod() {
        ScreenEffectsClient.init();
        VfxManager.init();
        RiftManager.init();
        ClientTickEvents.END.register(minecraft -> {
            CameraShakeManager.tick(minecraft);
            RiftManager.tick(minecraft);
            SoundFilterManager.tick(minecraft);
        });
    }
}
