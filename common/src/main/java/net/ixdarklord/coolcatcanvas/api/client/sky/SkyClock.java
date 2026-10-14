package net.ixdarklord.coolcatcanvas.api.client.sky;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * What drives a layer's rotation or animation. A rotation's speed is in degrees per unit of its clock.
 */
public enum SkyClock implements StringRepresentable {
    /** Seconds of real time: keeps going while the game is paused. */
    REAL_TIME,
    /** Seconds of the level's game time (20 ticks each): stops with the game. */
    GAME_TIME,
    /** Days of the dimension's clock, from 0 to 1 over one day and night: a speed of 360 turns once a day. */
    DAY_TIME,
    /** Vanilla's sun angle in degrees: rotating around {@code [0, 0, 1]} at speed 1 follows the sun. */
    SUN_ANGLE,
    /** Vanilla's moon angle in degrees: on 1.20.1, always opposite the sun. */
    MOON_ANGLE,
    /** Vanilla's star angle in degrees: on 1.20.1, the stars turn with the sun. */
    STAR_ANGLE;

    public static final Codec<SkyClock> CODEC = StringRepresentable.fromEnum(SkyClock::values);

    /** Whether it counts seconds, and so can time an animation's frames. */
    public boolean isTime() {
        return this == REAL_TIME || this == GAME_TIME;
    }

    @Override
    public String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
