package net.ixdarklord.coolcatcanvas.internal.core.client;

import net.ixdarklord.coolcatcanvas.internal.client.bossbar.BossBarClient;
import net.ixdarklord.coolcatcanvas.internal.client.config.BuiltInConfigScreens;
import net.ixdarklord.coolcatcanvas.internal.client.cutscene.CutsceneManager;
import net.ixdarklord.coolcatcanvas.internal.client.effect.ScreenEffectsClient;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.ThemeEffects;
import net.ixdarklord.coolcatcanvas.internal.client.armature.ArmatureClient;
import net.ixdarklord.coolcatcanvas.internal.client.camera.CameraShakeManager;
import net.ixdarklord.coolcatcanvas.internal.client.camera.CameraZoomManager;
import net.ixdarklord.coolcatcanvas.internal.client.vfx.RiftManager;
import net.ixdarklord.coolcatcanvas.internal.client.vfx.VfxManager;
import net.ixdarklord.coolcatcore.api.config.client.ConfigScreens;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientTickEvents;
import net.ixdarklord.coolcatcore.api.core.ClientModConstructor;

// The client entry point of CoolCatLib's visuals (the coolcatlib package).
public final class CoolCatCanvasClientConstructor implements ClientModConstructor {
    @Override
    public void onConstructMod() {
        ThemeEffects.init();
        // Config screens for when Glazed Menu, which sets its own, isn't installed.
        ConfigScreens.setFallback(new BuiltInConfigScreens());
        ScreenEffectsClient.init();
        VfxManager.init();
        CutsceneManager.init();
        RiftManager.init();
        ArmatureClient.init();
        BossBarClient.init();
        ClientTickEvents.END.register(minecraft -> {
            CameraShakeManager.tick(minecraft);
            CameraZoomManager.tick(minecraft);
            RiftManager.tick(minecraft);
        });
    }
}
