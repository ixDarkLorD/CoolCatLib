package net.ixdarklord.coolcatcanvas.api.client.sky;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * How a sky layer combines with what's drawn under it. Every mode fades with the layer's opacity, and leaves the
 * target's alpha alone except {@link #ALPHA}.
 */
public enum SkyBlend implements StringRepresentable {
    /** Painted over, by its alpha. */
    ALPHA(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA, SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA),
    /** Added to it: glows, stars, auroras. Black adds nothing. */
    ADDITIVE(SourceFactor.SRC_ALPHA, DestFactor.ONE, SourceFactor.ZERO, DestFactor.ONE),
    /** Multiplied with it: darkens and tints. White changes nothing. */
    MULTIPLY(SourceFactor.DST_COLOR, DestFactor.ZERO, SourceFactor.ZERO, DestFactor.ONE),
    /** The inverse of multiply: brightens without going past white. Black changes nothing. */
    SCREEN(SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_COLOR, SourceFactor.ZERO, DestFactor.ONE);

    public static final Codec<SkyBlend> CODEC = StringRepresentable.fromEnum(SkyBlend::values);

    private final SourceFactor sourceColor;
    private final DestFactor destColor;
    private final SourceFactor sourceAlpha;
    private final DestFactor destAlpha;

    SkyBlend(SourceFactor sourceColor, DestFactor destColor, SourceFactor sourceAlpha, DestFactor destAlpha) {
        this.sourceColor = sourceColor;
        this.destColor = destColor;
        this.sourceAlpha = sourceAlpha;
        this.destAlpha = destAlpha;
    }

    /** Enables blending with this mode's factors. Render thread only. */
    public void apply() {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(this.sourceColor, this.destColor, this.sourceAlpha, this.destAlpha);
    }

    @Override
    public String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
