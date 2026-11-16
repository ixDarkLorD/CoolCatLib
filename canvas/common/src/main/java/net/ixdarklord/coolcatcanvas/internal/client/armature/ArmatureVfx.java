package net.ixdarklord.coolcatcanvas.internal.client.armature;

import com.google.common.collect.MapMaker;
import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureTrail;
import net.ixdarklord.coolcatcanvas.api.client.vfx.VfxFrame;
import net.ixdarklord.coolcatcanvas.api.client.vfx.WorldVfx;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;

import java.util.ArrayList;
import java.util.Map;

/** Draws every armature trail that is still being fed, as one world effect. Trails are held weakly, with their animators. */
public final class ArmatureVfx {
    private static final Map<ArmatureTrail, Boolean> TRAILS = new MapMaker().weakKeys().makeMap();

    private ArmatureVfx() {
    }

    public static void init() {
        WorldVfx.register(CoolCatCanvas.rl("armature_trails"), ArmatureVfx::render);
    }

    public static void track(ArmatureTrail trail) {
        TRAILS.put(trail, Boolean.TRUE);
    }

    private static void render(VfxFrame frame) {
        if (TRAILS.isEmpty()) return;
        long gameTime = frame.level().getGameTime();
        for (ArmatureTrail trail : new ArrayList<>(TRAILS.keySet())) {
            if (!trail.fresh(gameTime)) continue;
            frame.draw(trail.material()).trail(trail.trail(), trail.width(), trail.headColor(), trail.tailColor());
        }
    }
}
