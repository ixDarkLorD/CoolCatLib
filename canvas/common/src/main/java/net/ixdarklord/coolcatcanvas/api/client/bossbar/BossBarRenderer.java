package net.ixdarklord.coolcatcanvas.api.client.bossbar;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Draws a boss bar of one style ({@link BossBarRenderers#register}), in place of the game's, on the HUD at the top of
 * the screen. Bars stack down from the top as the game's do, each below the last.
 */
@FunctionalInterface
public interface BossBarRenderer {
    /**
     * Draws the bar with its top edge at {@code y}, centred across the screen (as the game's are).
     *
     * @return how much height it took, so the next bar goes below it
     */
    int render(GuiGraphicsExtractor graphics, BossBarView bar, int y);
}
