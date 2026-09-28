package net.ixdarklord.coolcatcanvas.internal.client.sky;

import net.ixdarklord.coolcatcanvas.api.utils.Easing;
import net.minecraft.util.Mth;

import java.util.Arrays;

// A few floats that can jump to a value or ease towards one over time. The value array is read directly; it's never
// replaced, so callers may hold on to it.
final class Tween {
    final float[] value;
    private final float[] from;
    private final float[] to;
    private float elapsed;
    private float duration;
    private Easing easing = Easing.LINEAR;

    Tween(float... initial) {
        this.value = initial.clone();
        this.from = new float[initial.length];
        this.to = new float[initial.length];
    }

    /** Jumps to {@code values}; missing components are 0. Stops any animation. */
    void set(float... values) {
        this.duration = 0.0F;
        copy(values, this.value);
    }

    void animateTo(int ticks, Easing easing, float... target) {
        if (ticks <= 0) {
            this.set(target);
            return;
        }
        System.arraycopy(this.value, 0, this.from, 0, this.value.length);
        copy(target, this.to);
        this.elapsed = 0.0F;
        this.duration = ticks / 20.0F;
        this.easing = easing;
    }

    void update(float deltaTime) {
        if (this.duration <= 0.0F) return;
        this.elapsed += deltaTime;
        float progress = Math.min(1.0F, this.elapsed / this.duration);
        float eased = this.easing.apply(progress);
        for (int i = 0; i < this.value.length; i++) this.value[i] = Mth.lerp(eased, this.from[i], this.to[i]);
        if (progress >= 1.0F) this.duration = 0.0F;
    }

    private static void copy(float[] values, float[] into) {
        int count = Math.min(values.length, into.length);
        System.arraycopy(values, 0, into, 0, count);
        Arrays.fill(into, count, into.length, 0.0F);
    }
}
