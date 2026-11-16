package net.ixdarklord.coolcatcanvas.api.client.sky;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * The parts of vanilla's sky a skybox can hide. Hidden parts fade out as the skybox fades in, except {@link #VOID},
 * which disappears once the skybox is half visible.
 */
public enum VanillaSky implements StringRepresentable {
    /** The sky color overhead; hidden, it shows the fog color it fades into. */
    SKY,
    /** The glow around the sun at sunrise and sunset. */
    SUNRISE,
    SUN,
    MOON,
    STARS,
    /** The dark disc under the horizon, drawn while the camera is below it. */
    VOID,
    /** The End's textured sky. */
    END_SKY,
    /** The End's flashes, from later versions: the End of 1.21.1 has none, so hiding them does nothing. */
    END_FLASH;

    public static final Codec<VanillaSky> CODEC = StringRepresentable.fromEnum(VanillaSky::values);

    @Override
    public String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
