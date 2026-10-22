package net.ixdarklord.coolcatcanvas.internal.client.bossbar;

import net.ixdarklord.coolcatcanvas.api.client.bossbar.BossBarRenderer;
import net.ixdarklord.coolcatcanvas.api.client.bossbar.BossBarView;
import net.ixdarklord.coolcatcanvas.internal.bossbar.BossBarStylePayload;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientPlayerEvents;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Custom boss bars on the client: which style each bar is in (from the server's {@link BossBarStylePayload}), the
 * renderers registered for each style, and what each bar needs from one frame to the next to be drawn well: its
 * eased progress, its trail, how fresh its last hit is. {@code BossHealthOverlayMixin} asks it to draw.
 */
public final class BossBarClient {
    private static final Map<Identifier, BossBarRenderer> RENDERERS = new HashMap<>();
    private static final Map<UUID, Link> LINKS = new HashMap<>();
    // How long a trail holds after a hit before it drains, how fast it drains (of the bar, a tick), and how long a
    // hit stays fresh.
    private static final int TRAIL_HOLD = 12;
    private static final float TRAIL_DRAIN = 0.012F;
    private static final int HURT_TICKS = 10;

    private static final class Link {
        final Identifier style;
        final int entityId;
        float shown = -1.0F;
        float shownO;
        float trail;
        int hold;
        int hurt;
        int age;

        Link(Identifier style, int entityId) {
            this.style = style;
            this.entityId = entityId;
        }
    }

    private BossBarClient() {}

    public static void init() {
        ClientPlayerEvents.LEAVE.register(player -> LINKS.clear());
    }

    public static void register(Identifier style, BossBarRenderer renderer) {
        RENDERERS.put(style, renderer);
    }

    public static void onStyle(BossBarStylePayload payload) {
        if (payload.style() == null) {
            LINKS.remove(payload.bar());
        } else {
            LINKS.put(payload.bar(), new Link(payload.style(), payload.entityId()));
        }
    }

    /** Whether a bar is drawn by a renderer of its style (rather than as the game draws it). */
    public static boolean custom(BossEvent event) {
        Link link = LINKS.get(event.getId());
        return link != null && RENDERERS.containsKey(link.style);
    }

    /** Once a tick, from the overlay: bars ease and trails drain; bars no longer up are forgotten. */
    public static void tick(Iterable<? extends BossEvent> events) {
        Set<UUID> up = new HashSet<>();
        for (BossEvent event : events) {
            up.add(event.getId());
            Link link = LINKS.get(event.getId());
            if (link == null) continue;
            float progress = event.getProgress();
            link.age++;
            link.shownO = link.shown;
            if (link.shown < 0.0F) {
                link.shown = link.shownO = link.trail = progress;
                continue;
            }
            if (progress < link.shown - 0.0005F) {
                if (link.hold <= 0) link.trail = Math.max(link.trail, link.shown);
                link.hold = TRAIL_HOLD;
                link.hurt = HURT_TICKS;
            }
            link.shown += (progress - link.shown) * 0.5F;
            if (link.hurt > 0) link.hurt--;
            if (link.hold > 0) {
                link.hold--;
            } else {
                link.trail = Math.max(progress, link.trail - TRAIL_DRAIN);
            }
        }
        LINKS.keySet().retainAll(up);
    }

    /** Draws a custom bar with its top at y; returns how much height it took. */
    public static int draw(GuiGraphicsExtractor graphics, BossEvent event, int y) {
        Link link = LINKS.get(event.getId());
        BossBarRenderer renderer = link == null ? null : RENDERERS.get(link.style);
        if (renderer == null) return 0;
        Minecraft minecraft = Minecraft.getInstance();
        float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float shown = link.shown < 0.0F ? event.getProgress() : link.shownO + (link.shown - link.shownO) * partial;
        @Nullable Entity entity = link.entityId >= 0 && minecraft.level != null ? minecraft.level.getEntity(link.entityId) : null;
        BossBarView view = new BossBarView(event, link.style, entity, shown, Math.max(shown, link.trail),
                Math.max(0.0F, (link.hurt - partial) / HURT_TICKS), link.age + partial);
        return renderer.render(graphics, view, y);
    }
}
