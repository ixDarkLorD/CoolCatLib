package net.ixdarklord.coolcatcanvas.api.client.vfx;

/**
 * How a world effect combines with the scene behind it. None of them writes depth, so effects never cut holes in each
 * other.
 */
public enum VfxBlend {
    /**
     * Added to the scene: light, fire, magic. Overlapping pieces brighten each other, and black adds nothing, so a
     * greyscale glow on a black background needs no alpha channel. It washes out against a bright sky.
     */
    ADDITIVE(false),
    /** Painted over the scene by its alpha: smoke, dust, anything that should darken as well as lighten. */
    ALPHA(false),
    /**
     * Painted over like {@link #ALPHA}, taking the alpha from how bright each texel is: for glow textures drawn on
     * black with no alpha channel, which additive blending would lose against a bright sky. (Canvas's own textures
     * keep their brightness in alpha, so they need only {@link #ALPHA} for that.)
     */
    LUMINOUS(true);

    private final boolean luminanceAlpha;

    VfxBlend(boolean luminanceAlpha) {
        this.luminanceAlpha = luminanceAlpha;
    }

    /** Whether the shader takes each texel's alpha from its brightness. */
    public boolean luminanceAlpha() {
        return this.luminanceAlpha;
    }
}
