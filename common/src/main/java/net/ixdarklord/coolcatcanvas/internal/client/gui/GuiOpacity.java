package net.ixdarklord.coolcatcanvas.internal.client.gui;

/**
 * The opacity {@code FadedItems} draws at now: the GUI items made while it is under 1 remember it and are blitted with
 * it ({@code GuiItemRenderStateMixin}, {@code GuiRendererMixin}), and, for its {@code withOpacity}, the text and filled
 * rectangles made then are faded by it ({@code GuiTextRenderStateMixin}, {@code ColoredRectangleRenderStateMixin}).
 * Render thread only.
 */
public final class GuiOpacity {
    private static final int DEPTH = 32;
    // Each push: the opacity, and whether it was for items only.
    private static final float[] OPACITY = new float[DEPTH];
    private static final boolean[] ITEMS_ONLY = new boolean[DEPTH];
    private static int depth;

    private GuiOpacity() {}

    public static void push(float opacity, boolean itemsOnly) {
        if (depth >= DEPTH) throw new IllegalStateException("FadedItems: opacity pushed " + DEPTH + " deep without a pop");
        OPACITY[depth] = opacity;
        ITEMS_ONLY[depth] = itemsOnly;
        depth++;
    }

    public static void pop() {
        if (depth == 0) throw new IllegalStateException("FadedItems: opacity popped without a push");
        depth--;
    }

    /** The opacity an item drawn now is at. */
    public static float items() {
        float opacity = 1.0F;
        for (int i = 0; i < depth; i++) opacity *= OPACITY[i];
        return opacity;
    }

    /** The opacity text and rectangles drawn now are at (what {@code withOpacity} asked for). */
    public static float everything() {
        float opacity = 1.0F;
        for (int i = 0; i < depth; i++) {
            if (!ITEMS_ONLY[i]) opacity *= OPACITY[i];
        }
        return opacity;
    }

    /** A color with its alpha scaled by {@link #everything}; {@code text} drops what would be too faint to draw well. */
    public static int fade(int argb, boolean text) {
        if (depth == 0) return argb;
        float opacity = everything();
        if (opacity >= 1.0F) return argb;
        int alpha = Math.round((argb >>> 24) * opacity);
        // The game draws text whose alpha is under 4 at full strength, so faint text goes away instead.
        if (text && alpha < 5) alpha = 0;
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    /** A GUI item's render state that knows the opacity it was drawn at. */
    public interface FadedItem {
        float coolcatlib$opacity();
    }
}
