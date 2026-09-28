package net.ixdarklord.coolcatcanvas.internal.client.effect;

import net.ixdarklord.coolcatcanvas.api.client.effect.ScreenEffect;
import net.ixdarklord.coolcatcanvas.api.event.v2.client.ScreenEffectEvents;
import net.ixdarklord.coolcatcanvas.api.utils.Easing;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.ixdarklord.coolcatcanvas.internal.effect.ScreenEffectPayload;

// Applies the server's screen effect commands. Unknown effects are skipped: the client may lack the mod that adds them.
public final class ScreenEffectPackets {
    private ScreenEffectPackets() {}

    public static void handle(ScreenEffectPayload payload) {
        ScreenEffect effect = ScreenEffectManager.get(payload.effect());
        if (effect == null) {
            CoolCatCanvas.LOGGER.debug("Server sent a command for unknown screen effect {}", payload.effect());
            return;
        }
        float first = payload.values().length > 0 ? payload.values()[0] : 0.0F;
        ScreenEffectManager.runAs(ScreenEffectEvents.ToggleCause.SERVER, () -> apply(effect, payload, first));
    }

    private static void apply(ScreenEffect effect, ScreenEffectPayload payload, float first) {
        switch (payload.action()) {
            case ENABLE -> effect.enable();
            case DISABLE -> effect.disable();
            case ENABLE_FOR -> effect.enableFor(payload.ticks());
            case ENABLE_INSTANTLY -> effect.enableInstantly();
            case DISABLE_INSTANTLY -> effect.disableInstantly();
            case SET_STRENGTH -> effect.animateStrength(first, payload.ticks(), Easing.SINE_IN_OUT);
            case SET_UNIFORM -> effect.uniform(payload.uniform()).animateTo(payload.ticks(), payload.values());
            case RESET_UNIFORMS -> effect.resetUniforms();
        }
    }
}
