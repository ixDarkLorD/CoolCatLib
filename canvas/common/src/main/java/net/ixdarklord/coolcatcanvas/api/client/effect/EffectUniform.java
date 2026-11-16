package net.ixdarklord.coolcatcanvas.api.client.effect;

import net.ixdarklord.coolcatcanvas.api.utils.Easing;
import org.joml.Matrix4fc;
import org.joml.Vector2fc;
import org.joml.Vector3fc;
import org.joml.Vector4fc;

/**
 * One named uniform of a screen effect, written into every pass that declares it.
 * <p>
 * Until it's given a value, a uniform keeps the default its effect definition declares. Its value comes from, in order
 * of precedence: a {@linkplain #bind binding}, a running {@linkplain #animateTo animation}, then the last value
 * {@linkplain #set set}. Values are converted to the declared type: floats are truncated into int uniforms, and
 * missing components are 0.
 */
public interface EffectUniform {
    String name();

    /** Sets a float, vec2, vec3 or vec4 (or a 4x4 matrix from 16 column-major floats). Stops any animation. */
    EffectUniform set(float... values);

    /** Sets an int or ivec3. Stops any animation. */
    EffectUniform setInt(int... values);

    default EffectUniform set(Vector2fc value) {
        return this.set(value.x(), value.y());
    }

    default EffectUniform set(Vector3fc value) {
        return this.set(value.x(), value.y(), value.z());
    }

    default EffectUniform set(Vector4fc value) {
        return this.set(value.x(), value.y(), value.z(), value.w());
    }

    default EffectUniform set(Matrix4fc value) {
        return this.set(value.get(new float[16]));
    }

    /** Sets an RGBA color from a packed ARGB int, as a vec4. */
    default EffectUniform setColor(int argb) {
        return this.set((argb >> 16 & 0xFF) / 255.0F, (argb >> 8 & 0xFF) / 255.0F, (argb & 0xFF) / 255.0F, (argb >>> 24) / 255.0F);
    }

    /**
     * Moves the value from where it is now to {@code target} over {@code ticks} ticks of real time (20 per second).
     */
    EffectUniform animateTo(Easing easing, int ticks, float... target);

    default EffectUniform animateTo(int ticks, float... target) {
        return this.animateTo(Easing.SINE_IN_OUT, ticks, target);
    }

    /** Computes a float value every frame, overriding set and animated values until {@link #unbind()}. */
    EffectUniform bind(FloatBinding binding);

    /** Computes a vector value every frame, overriding set and animated values until {@link #unbind()}. */
    EffectUniform bind(VectorBinding binding);

    EffectUniform unbind();

    /** Drops the binding, animation and set value: the definition's default applies again. */
    EffectUniform reset();

    /** The current first component: the definition's default if never set, 0 before the effect has loaded. */
    float get();

    /** The current components, as floats: the definition's default if never set, empty before the effect has loaded. */
    float[] getAll();

    @FunctionalInterface
    interface FloatBinding {
        float get(EffectContext context);
    }

    @FunctionalInterface
    interface VectorBinding {
        /** Writes the components into {@code out}, which holds 16 floats (enough for a matrix). */
        void get(EffectContext context, float[] out);
    }
}
