package net.ixdarklord.coolcatcanvas.api.client.sky;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * What a sky layer draws. Every type can take its own {@code fragment_shader} instead of the built-in one; the type then
 * only decides the shape drawn and whether a texture is bound.
 */
public enum SkyLayerType implements StringRepresentable {
    /**
     * A texture wrapped around the whole sky as a cube: one image holding the six square faces in a 3x2 grid, north,
     * east and south on the top row, west, up and down on the bottom one. Side faces are upright as seen from inside;
     * up and down are as seen while facing north (so north touches the bottom edge of up and the top edge of down).
     * Frames of an {@linkplain SkyLayerDefinition.Animation animation} are stacked vertically, each its own 3x2 grid.
     */
    CUBEMAP(true, false),
    /**
     * A texture wrapped around the whole sky as an equirectangular panorama (twice as wide as it's tall): north in the
     * middle, east to its right, the zenith along the top edge.
     */
    PANORAMA(true, false),
    /**
     * A single image on a square facing the camera from the sky, like vanilla's sun and moon: a planet, a moon, a
     * logo. It sits at the zenith before the layer's orientation and rotation; its size is an angle.
     */
    SPRITE(true, true),
    /**
     * A vertical color gradient over the whole sky, no texture: {@code params} 0, 1 and 2 are the zenith, horizon and
     * nadir colors, and the first component of param 3 sharpens the horizon (1 for linear, higher for a thinner band).
     */
    GRADIENT(false, false),
    /**
     * A procedural sky from the layer's own fragment shader, over the whole sky. Binds a texture only if it has one.
     */
    SHADER(false, false);

    public static final Codec<SkyLayerType> CODEC = StringRepresentable.fromEnum(SkyLayerType::values);

    private final boolean needsTexture;
    private final boolean sprite;

    SkyLayerType(boolean needsTexture, boolean sprite) {
        this.needsTexture = needsTexture;
        this.sprite = sprite;
    }

    /** Whether a layer of this type must have a texture. */
    public boolean needsTexture() {
        return this.needsTexture;
    }

    /** Whether it's drawn as a square in the sky, rather than around the whole sky. */
    public boolean isSprite() {
        return this.sprite;
    }

    @Override
    public String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
