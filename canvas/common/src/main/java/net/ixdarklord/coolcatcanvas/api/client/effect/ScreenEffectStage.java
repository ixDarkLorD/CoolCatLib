package net.ixdarklord.coolcatcanvas.api.client.effect;

/**
 * When in the frame a screen effect is drawn.
 */
public enum ScreenEffectStage {
    /**
     * Over the world and the held item, under the HUD and screens: where vanilla draws its creeper and spider views.
     * Only while a world is being rendered. The depth buffer still holds the world's depth here.
     */
    WORLD,
    /**
     * Over everything, HUD and screens included. Also runs in menus, with no world loaded.
     */
    SCREEN
}
