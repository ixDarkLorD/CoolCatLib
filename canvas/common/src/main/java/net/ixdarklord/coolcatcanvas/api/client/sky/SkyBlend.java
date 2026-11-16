package net.ixdarklord.coolcatcanvas.api.client.sky;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * How a sky layer combines with what's drawn under it. Every mode fades with the layer's opacity, and leaves the
 * target's alpha alone except {@link #ALPHA}.
 */
public enum SkyBlend implements StringRepresentable {
    /** Painted over, by its alpha. */
    ALPHA(new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA, BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_ALPHA)),
    /** Added to it: glows, stars, auroras. Black adds nothing. */
    ADDITIVE(new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE, BlendFactor.ZERO, BlendFactor.ONE)),
    /** Multiplied with it: darkens and tints. White changes nothing. */
    MULTIPLY(new BlendFunction(BlendFactor.DST_COLOR, BlendFactor.ZERO, BlendFactor.ZERO, BlendFactor.ONE)),
    /** The inverse of multiply: brightens without going past white. Black changes nothing. */
    SCREEN(new BlendFunction(BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_COLOR, BlendFactor.ZERO, BlendFactor.ONE));

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
