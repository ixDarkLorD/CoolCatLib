package net.ixdarklord.coolcatcanvas.internal.core;

import net.ixdarklord.coolcatcanvas.internal.effect.ScreenEffectNetwork;
import net.ixdarklord.coolcatcanvas.internal.sky.SkyboxNetwork;
import net.ixdarklord.coolcatcore.api.core.ModConstructor;

// CoolCat Canvas's common entry point, constructed by CoolCat Core like any other mod's.
public final class CoolCatCanvasConstructor implements ModConstructor {
    @Override
    public void onConstructMod() {
        ScreenEffectNetwork.init();
        SkyboxNetwork.init();
    }
}
