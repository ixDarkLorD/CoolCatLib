package net.ixdarklord.coolcatlib.api.util;

public class MathUtils {
    public static float cycledBetweenValues(float min, float max, float speed, float time, boolean reverse) {
        float amplitude = (max - min) / 2;
        float offset = (max + min) / 2;
        if (reverse) return (float) (amplitude * Math.cos(2 * Math.PI * speed * time) + offset);
        return (float) (amplitude * Math.sin(2 * Math.PI * speed * time) + offset);
    }
}
