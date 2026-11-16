package net.ixdarklord.coolcatcanvas.api.client.vfx;

/**
 * Where in a frame's world effects a shape is drawn, given to {@link VfxFrame#draw(VfxMaterial, VfxLayer)}. World effects
 * write no depth of their own, so what's drawn later covers what's drawn earlier wherever they overlap, nearer or not.
 * Layers say what that order is:
 * <ul>
 *     <li>Layers are drawn from the lowest {@link #order()} to the highest, so a higher layer always covers a lower one.</li>
 *     <li>Within a layer, everything painted over the scene ({@link VfxBlend#ALPHA}, {@link VfxBlend#LUMINOUS}) is drawn
 *     first and everything added to it ({@link VfxBlend#ADDITIVE}) last; additive shapes look the same in any order.</li>
 *     <li>In a plain layer, the painted-over shapes are drawn material by material, each material where it was first drawn
 *     in the frame and its shapes in the order they were drawn. That's cheap, and right when one material's shapes
 *     never need to cover another's.</li>
 *     <li>In a {@link #sorted} layer, the painted-over shapes of every material are drawn back to front, the farthest from
 *     the camera first: shapes of different textures cover each other as their distance says. It costs a sort of the
 *     layer's shapes each frame (by each one's middle), and a draw call wherever the material changes along the way.</li>
 * </ul>
 * Particles and the shapes of {@link WorldVfx} are drawn in {@link #DEFAULT}.
 * <pre>{@code
 * VfxLayer HANDS = VfxLayer.sorted(10);   // the hands and arms of a creature, which reach in front of each other
 * frame.draw(HAND, HANDS).quad(...);
 * frame.draw(ARM, HANDS).quad(...);
 * }</pre>
 *
 * @param order  where the layer is drawn: lower first
 * @param sorted whether its painted-over shapes are drawn back to front across materials
 */
public record VfxLayer(int order, boolean sorted) {
    /** Layer 0, not sorted: where shapes go without a layer. */
    public static final VfxLayer DEFAULT = new VfxLayer(0, false);

    /** A plain layer: drawn material by material in first-use order. */
    public static VfxLayer of(int order) {
        return new VfxLayer(order, false);
    }

    /** A sorted layer: its painted-over shapes, of every material, drawn from the farthest to the nearest. */
    public static VfxLayer sorted(int order) {
        return new VfxLayer(order, true);
    }
}
