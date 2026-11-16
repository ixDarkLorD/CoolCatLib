package net.ixdarklord.coolcatcanvas.internal.client.sky;

import net.ixdarklord.coolcatcanvas.api.client.sky.SkyLayer;
import net.ixdarklord.coolcatcanvas.api.client.sky.Skybox;
import net.ixdarklord.coolcatcanvas.api.utils.Easing;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.ixdarklord.coolcatcanvas.internal.sky.SkyboxPayload;

// Applies the server's skybox commands. Unknown skyboxes are skipped: the client may lack the mod or pack adding them.
public final class SkyboxPackets {
    private SkyboxPackets() {}

    public static void handle(SkyboxPayload payload) {
        Skybox skybox = SkyboxManager.get(payload.skybox());
        if (skybox == null) {
            CoolCatCanvas.LOGGER.debug("Server sent a command for unknown skybox {}", payload.skybox());
            return;
        }
        float first = payload.values().length > 0 ? payload.values()[0] : 0.0F;
        switch (payload.action()) {
            case ENABLE -> skybox.enable();
            case DISABLE -> skybox.disable();
            case ENABLE_FOR -> skybox.enableFor(payload.ticks());
            case ENABLE_INSTANTLY -> skybox.enableInstantly();
            case DISABLE_INSTANTLY -> skybox.disableInstantly();
            case SET_LAYER_VISIBLE -> skybox.layer(payload.layer()).setVisible(first != 0.0F);
            case SET_LAYER_ALPHA -> skybox.layer(payload.layer()).animateAlpha(first, payload.ticks(), Easing.SINE_IN_OUT);
            case SET_LAYER_PARAM -> {
                if (payload.index() >= 0 && payload.index() < 4) {
                    skybox.layer(payload.layer()).animateParam(payload.index(), payload.ticks(), Easing.SINE_IN_OUT, payload.values());
                }
            }
            case RESET_LAYER -> {
                SkyLayer layer = skybox.layer(payload.layer());
                layer.setVisible(true).setAlpha(1.0F).resetTint().resetParams();
            }
            case TOGGLE -> skybox.toggle();
            case SET_ENABLED -> skybox.setEnabled(first != 0.0F);
        }
    }
}
