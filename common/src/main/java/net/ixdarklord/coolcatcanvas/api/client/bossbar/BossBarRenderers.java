package net.ixdarklord.coolcatcanvas.api.client.bossbar;

import net.ixdarklord.coolcatcanvas.internal.client.bossbar.BossBarClient;
import net.minecraft.resources.Identifier;

/**
 * The client half of custom boss bars: how each style is drawn. A bar the server styled
 * ({@code CustomBossBars.style}, {@code EntityBossBar}) is drawn by the renderer registered for its style; a style with
 * none falls back to the game's own bar.
 * <pre>{@code
 * BossBarRenderers.register(MyMod.id("warden"), TexturedBossBar.builder(MyMod.id("textures/gui/boss/warden.png"), 240, 48)
 *         .frame(0, 0, 240, 30).track(35, 12, 180, 7).fill(0, 32).trail(0, 40)
 *         .build());
 * }</pre>
 */
public final class BossBarRenderers {
    private BossBarRenderers() {}

    /** Draws bars of a style with a renderer: {@link TexturedBossBar} for one made of a texture, or one's own. */
    public static void register(Identifier style, BossBarRenderer renderer) {
        BossBarClient.register(style, renderer);
    }
}
