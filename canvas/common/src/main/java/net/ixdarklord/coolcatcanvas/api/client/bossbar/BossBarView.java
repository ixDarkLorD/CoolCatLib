package net.ixdarklord.coolcatcanvas.api.client.bossbar;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * A boss bar as a {@link BossBarRenderer} draws it this frame: what the game knows of it, and what CoolCatLib keeps
 * for drawing it well.
 *
 * @param event    the bar as the game keeps it: name, colour, overlay, the server's progress
 * @param style    the style it is drawn in
 * @param entity   the entity it belongs to, while it is loaded here; null if none was given, or it is out of sight
 * @param progress how full it is, 0 to 1, eased smoothly towards the server's
 * @param trail    what it was a moment ago, 0 to 1, at least {@code progress}: hold it a moment after a hit, then
 *                 drain it, to show what was lost
 * @param hurt     how freshly it was hit, 1 the moment of a hit falling to 0 over half a second
 * @param time     ticks since the bar appeared, with the partial tick, for animating it
 */
public record BossBarView(BossEvent event, Identifier style, @Nullable Entity entity, float progress, float trail, float hurt, float time) {
    public Component name() {
        return this.event.getName();
    }
}
