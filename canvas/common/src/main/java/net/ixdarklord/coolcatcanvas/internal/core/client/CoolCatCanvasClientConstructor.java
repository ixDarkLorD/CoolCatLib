package net.ixdarklord.coolcatcanvas.internal.core.client;

import net.ixdarklord.coolcatcanvas.internal.client.effect.ScreenEffectsClient;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.ThemeEffects;
import net.ixdarklord.coolcatcore.api.core.ClientModConstructor;

// CoolCatLib: Canvas's client entry point.
public final class CoolCatCanvasClientConstructor implements ClientModConstructor {
    @Override
    public void onConstructMod() {
        ThemeEffects.init();
        ScreenEffectsClient.init();
    }
}
