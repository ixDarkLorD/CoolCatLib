package net.ixdarklord.coolcatcanvas.api.client.sky;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * When in vanilla's sky a layer is drawn. In the End, both stages are drawn right after the End sky.
 */
public enum SkyLayerStage implements StringRepresentable {
    /** Over the sky color and the sunrise glow, under the sun, moon and stars: backgrounds. */
    BEHIND_CELESTIALS,
    /** Over the sun, moon and stars: clouds of color, auroras, overlays. */
    ABOVE_CELESTIALS;

    public static final Codec<SkyLayerStage> CODEC = StringRepresentable.fromEnum(SkyLayerStage::values);

    @Override
    public String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
