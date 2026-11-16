package net.ixdarklord.coolcatcanvas.api.bossbar;

import net.ixdarklord.coolcatcanvas.internal.bossbar.StyledBossEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Boss bars drawn a mod's own way, from the server. A bar is given a style, an id the client draws it by
 * ({@code BossBarRenderers.register} on the client); clients that know no renderer for it, or have no CoolCatLib,
 * draw the game's own bar, so a styled bar is always safe to show.
 * <pre>{@code
 * // A boss's own bar, kept by the boss:
 * private final ServerBossEvent bar = new ServerBossEvent(getUUID(), getDisplayName(), BossBarColor.RED, BossBarOverlay.PROGRESS);
 * CustomBossBars.style(bar, MyMod.id("warden"), this);  // in its constructor
 *
 * // Or let CoolCatLib keep one for any living entity, following its name and health:
 * EntityBossBar.of(entity).style(MyMod.id("warden")).range(48).show();
 * }</pre>
 * The style can be changed or taken off ({@code null}) at any time; players seeing the bar are told at once.
 */
public final class CustomBossBars {
    private CustomBossBars() {}

    /** Draws a bar in a style; null puts it back to the game's own bar. */
    public static void style(ServerBossEvent bar, @Nullable Identifier style) {
        style(bar, style, null);
    }

    /**
     * Draws a bar in a style, linked to an entity: the renderer can show it (a face, a model, its own state) while it
     * is loaded on the client.
     */
    public static void style(ServerBossEvent bar, @Nullable Identifier style, @Nullable Entity entity) {
        ((StyledBossEvent) bar).coolcatlib$setStyle(style, entity == null ? -1 : entity.getId());
    }

    /** The style a bar is drawn in, or null for the game's own. */
    public static @Nullable Identifier styleOf(ServerBossEvent bar) {
        return ((StyledBossEvent) bar).coolcatlib$style();
    }
}
