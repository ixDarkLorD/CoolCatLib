package net.ixdarklord.coolcatcanvas.api.client.blockentity;

/**
 * How much of a block entity is drawn, by how far off it is: {@link #FULL} near, then {@link #REDUCED}, then
 * {@link #MINIMAL}. A {@link BlockRenderer} sets the distance of each with {@link BlockRenderer#detail}, and its state
 * says which one it's drawn at ({@link BlockRenderState#detail}). Lower is more detailed; a mod can use more levels of
 * its own (3, 4, ...) the same way.
 */
public final class Detail {
    /** Everything: animation every frame, effects, text. */
    public static final int FULL = 0;
    /** Fewer effects; an armature animates every other frame. */
    public static final int REDUCED = 1;
    /** The bare model; an armature animates every fourth frame and drops its attachments. */
    public static final int MINIMAL = 2;

    private Detail() {}
}
