package net.ixdarklord.coolcatcanvas.api.client.vfx;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;

/**
 * How a world effect combines with the scene behind it. None of them writes depth, so effects never cut holes in each
 * other.
 */
public enum VfxBlend {
    /**
     * Added to the scene: light, fire, magic. Overlapping pieces brighten each other, and black adds nothing, so a
     * greyscale glow on a black background needs no alpha channel. It washes out against a bright sky.
     */
    ADDITIVE(new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE, SourceFactor.ZERO, DestFactor.ONE), false),
    /** Painted over the scene by its alpha: smoke, dust, anything that should darken as well as lighten. */
    ALPHA(new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA, SourceFactor.ZERO, DestFactor.ONE), false),
    /**
     * Painted over like {@link #ALPHA}, taking the alpha from how bright each texel is: for glow textures drawn on
     * black with no alpha channel, which additive blending would lose against a bright sky. (Canvas's own textures
     * keep their brightness in alpha, so they need only {@link #ALPHA} for that.)
     */
    LUMINOUS(new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA, SourceFactor.ZERO, DestFactor.ONE), true);

    private final BlendFunction function;
    private final boolean luminanceAlpha;

    VfxBlend(BlendFunction function, boolean luminanceAlpha) {
        this.function = function;
        this.luminanceAlpha = luminanceAlpha;
    }

    public BlendFunction function() {
        return this.function;
    }

    /** Whether the shader takes each texel's alpha from its brightness. */
    public boolean luminanceAlpha() {
        return this.luminanceAlpha;
    }
}
