package net.ixdarklord.coolcatcanvas.internal.client.sky;

import net.ixdarklord.coolcatcanvas.api.client.sky.SkyClock;
import net.ixdarklord.coolcatcanvas.api.client.sky.SkyLayerDefinition;
import org.joml.Matrix4f;

// The clocks, weather and camera layers read while drawing, refreshed once per frame (the camera and angles as the sky
// pass starts). Times are doubles: game time grows past what a float counts exactly within a few in-game days.
final class SkyFrame {
    double realSeconds;
    double gameSeconds;
    float dayTime;
    float rain;
    float thunder;
    float sunAngle;
    float moonAngle;
    float starAngle;
    final Matrix4f view = new Matrix4f();
    final Matrix4f projection = new Matrix4f();

    /** The clock's reading: seconds, days, or degrees. */
    double read(SkyClock clock) {
        return switch (clock) {
            case REAL_TIME -> this.realSeconds;
            case GAME_TIME -> this.gameSeconds;
            case DAY_TIME -> this.dayTime / SkyLayerDefinition.DayFade.DAY_LENGTH;
            case SUN_ANGLE -> this.sunAngle;
            case MOON_ANGLE -> this.moonAngle;
            case STAR_ANGLE -> this.starAngle;
        };
    }
}
