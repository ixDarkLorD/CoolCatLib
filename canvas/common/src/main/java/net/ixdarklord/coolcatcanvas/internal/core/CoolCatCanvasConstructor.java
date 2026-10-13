package net.ixdarklord.coolcatcanvas.internal.core;

import net.ixdarklord.coolcatcanvas.internal.cutscene.CutsceneNetwork;
import net.ixdarklord.coolcatcanvas.internal.effect.ScreenEffectNetwork;
import net.ixdarklord.coolcatcanvas.internal.sky.SkyboxNetwork;
import net.ixdarklord.coolcatcanvas.internal.vfx.VfxNetwork;
import net.ixdarklord.coolcatcore.api.core.ModConstructor;

// CoolCatLib: Canvas's common entry point, constructed by CoolCatLib: Core like any other mod's.
public final class CoolCatCanvasConstructor implements ModConstructor {
    @Override
    public void onConstructMod() {
        ScreenEffectNetwork.init();
        SkyboxNetwork.init();
        VfxNetwork.init();
        CutsceneNetwork.init();
    }
}
