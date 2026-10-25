package net.ixdarklord.coolcatcanvas.internal.core;

import net.ixdarklord.coolcatcanvas.internal.bossbar.BossBarNetwork;
import net.ixdarklord.coolcatcanvas.internal.bossbar.EntityBossBars;
import net.ixdarklord.coolcatcanvas.internal.cutscene.CutsceneNetwork;
import net.ixdarklord.coolcatcanvas.internal.effect.ScreenEffectNetwork;
import net.ixdarklord.coolcatcanvas.internal.armature.ArmatureNetwork;
import net.ixdarklord.coolcatcanvas.internal.sky.SkyboxNetwork;
import net.ixdarklord.coolcatcanvas.internal.sound.SoundFilterNetwork;
import net.ixdarklord.coolcatcanvas.internal.vfx.VfxNetwork;
import net.ixdarklord.coolcatcore.api.core.ModConstructor;

// The common entry point of CoolCatLib's visuals (the coolcatlib package), constructed right after Core's.
public final class CoolCatCanvasConstructor implements ModConstructor {
    @Override
    public void onConstructMod() {
        ScreenEffectNetwork.init();
        SkyboxNetwork.init();
        VfxNetwork.init();
        CutsceneNetwork.init();
        ArmatureNetwork.init();
        BossBarNetwork.init();
        SoundFilterNetwork.init();
        EntityBossBars.init();
    }
}
