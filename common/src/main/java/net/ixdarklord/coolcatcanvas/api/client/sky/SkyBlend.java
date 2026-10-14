package net.ixdarklord.coolcatcanvas.api.client.sky;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * How a sky layer combines with what's drawn under it. Every mode fades with the layer's opacity, and leaves the
 * target's alpha alone except {@link #ALPHA}.
 */
public enum SkyBlend implements StringRepresentable {
    /** Painted over, by its alpha. */
    ALPHA(new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA, SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA)),
    /** Added to it: glows, stars, auroras. Black adds nothing. */
    ADDITIVE(new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE, SourceFactor.ZERO, DestFactor.ONE)),
    /** Multiplied with it: darkens and tints. White changes nothing. */
    MULTIPLY(new BlendFunction(SourceFactor.DST_COLOR, DestFactor.ZERO, SourceFactor.ZERO, DestFactor.ONE)),
    /** The inverse of multiply: brightens without going past white. Black changes nothing. */
    SCREEN(new BlendFunction(SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_COLOR, SourceFactor.ZERO, DestFactor.ONE));

    public static final Codec<SkyBlend> CODEC = StringRepresentable.fromEnum(SkyBlend::values);

    private final BlendFunction function;

    SkyBlend(BlendFunction function) {
        this.function = function;
    }

    public BlendFunction function() {
        return this.function;
    }

    @Override
    public String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
