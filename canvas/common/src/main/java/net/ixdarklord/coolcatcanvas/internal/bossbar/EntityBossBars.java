package net.ixdarklord.coolcatcanvas.internal.bossbar;

import net.ixdarklord.coolcatcanvas.api.bossbar.EntityBossBar;
import net.ixdarklord.coolcatcore.api.event.v2.common.ServerLifecycleEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.ServerTickEvents;

import java.util.ArrayList;
import java.util.List;

/** Keeps the entity bars that are showing, every server tick. */
public final class EntityBossBars {
    private static final List<EntityBossBar> RUNNING = new ArrayList<>();

    private EntityBossBars() {}

    public static void init() {
        ServerTickEvents.END.register(server -> RUNNING.removeIf(bar -> !bar.tick()));
        ServerLifecycleEvents.STOPPED.register(server -> RUNNING.clear());
    }

    public static void add(EntityBossBar bar) {
        RUNNING.add(bar);
    }
}
