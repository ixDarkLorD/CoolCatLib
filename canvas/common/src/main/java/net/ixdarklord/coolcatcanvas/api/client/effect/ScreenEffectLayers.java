package net.ixdarklord.coolcatcanvas.api.client.effect;

import java.util.List;

/**
 * The order screen effects are drawn in, like layers in an image editor: the first layer is drawn first, straight
 * over the game, and each later one processes the image the layers before it produced. So a blur above a vignette
 * blurs the vignette's edge too; below it, the edge stays sharp.
 * <p>
 * Every registered effect has a layer, whether it's on or not. The order starts out sorted by
 * {@linkplain ScreenEffect#priority priority} (then registration order), and stays as rearranged once changed,
 * from here or by a player in the effects screen, which remembers it. Scopes come first: all {@code WORLD} effects
 * are drawn before any {@code SCREEN} effect, whatever their layers.
 * <p>
 * Only use it from the render thread.
 */
public interface ScreenEffectLayers {
    /** Every effect, from the bottom layer (drawn first) to the top (drawn last). */
    List<ScreenEffect> order();

    /** The effects in a scope that are drawn this frame, bottom to top. */
    List<ScreenEffect> visible(ScreenEffectScope scope);

    /** The effect's layer: 0 is the bottom. */
    int indexOf(ScreenEffect effect);

    /** Moves an effect to a layer, 0 being the bottom; the index is clamped. */
    void moveTo(ScreenEffect effect, int index);

    /** Moves it one layer up (drawn later), past the next effect of its own scope. */
    void moveUp(ScreenEffect effect);

    /** Moves it one layer down (drawn earlier), past the previous effect of its own scope. */
    void moveDown(ScreenEffect effect);

    default void bringToTop(ScreenEffect effect) {
        this.moveTo(effect, Integer.MAX_VALUE);
    }

    default void sendToBottom(ScreenEffect effect) {
        this.moveTo(effect, 0);
    }

    /** Whether the order was rearranged, rather than following priorities. */
    boolean isCustomized();

    /** Sorts the layers by priority again. */
    void resetOrder();
}
