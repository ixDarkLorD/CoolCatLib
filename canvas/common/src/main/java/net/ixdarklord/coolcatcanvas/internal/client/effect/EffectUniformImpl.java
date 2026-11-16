package net.ixdarklord.coolcatcanvas.internal.client.effect;

import net.ixdarklord.coolcatcanvas.api.client.effect.EffectContext;
import net.ixdarklord.coolcatcanvas.api.client.effect.EffectUniform;
import net.ixdarklord.coolcatcanvas.api.event.v2.client.ScreenEffectEvents;
import net.ixdarklord.coolcatcanvas.api.utils.Easing;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

// A uniform's value lives in 16 floats, enough for a matrix; ints are stored as floats and truncated on upload.
final class EffectUniformImpl implements EffectUniform {
    private static final int CAPACITY = 16;

    private final ScreenEffectImpl effect;
    private final String name;
    private final float[] value = new float[CAPACITY];
    // Components set so far; 0 means the definition's default applies.
    private int size;

    private float @Nullable [] animationFrom;
    private float @Nullable [] animationTo;
    private float animationElapsed;
    private float animationDuration;
    private Easing animationEasing = Easing.LINEAR;

    private @Nullable FloatBinding floatBinding;
    private @Nullable VectorBinding vectorBinding;

    private final float[] scratch = new float[CAPACITY];

    EffectUniformImpl(ScreenEffectImpl effect, String name) {
        this.effect = effect;
        this.name = name;
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public EffectUniform set(float... values) {
        this.stopAnimation();
        this.store(values);
        return this.changed();
    }

    @Override
    public EffectUniform setInt(int... values) {
        float[] floats = new float[values.length];
        for (int i = 0; i < values.length; i++) floats[i] = values[i];
        return this.set(floats);
    }

    @Override
    public EffectUniform animateTo(Easing easing, int ticks, float... target) {
        if (ticks <= 0) return this.set(target);
        float[] from = new float[target.length];
        float[] current = this.getAll();
        System.arraycopy(current, 0, from, 0, Math.min(current.length, from.length));
        this.animationFrom = from;
        this.animationTo = target.clone();
        this.animationElapsed = 0.0F;
        this.animationDuration = ticks / 20.0F;
        this.animationEasing = easing;
        return this.changed();
    }

    @Override
    public EffectUniform bind(FloatBinding binding) {
        this.floatBinding = binding;
        this.vectorBinding = null;
        return this.changed();
    }

    @Override
    public EffectUniform bind(VectorBinding binding) {
        this.vectorBinding = binding;
        this.floatBinding = null;
        return this.changed();
    }

    @Override
    public EffectUniform unbind() {
        this.floatBinding = null;
        this.vectorBinding = null;
        return this.changed();
    }

    @Override
    public EffectUniform reset() {
        this.floatBinding = null;
        this.vectorBinding = null;
        this.stopAnimation();
        Arrays.fill(this.value, 0.0F);
        this.size = 0;
        return this.changed();
    }

    private EffectUniform changed() {
        ScreenEffectEvents.UNIFORM_CHANGED.invoker().onUniformChanged(this.effect, this);
        return this;
    }

    @Override
    public float get() {
        float[] all = this.getAll();
        return all.length > 0 ? all[0] : 0.0F;
    }

    @Override
    public float[] getAll() {
        if (this.size > 0) return Arrays.copyOf(this.value, this.size);
        float[] fallback = this.effect.defaultValue(this.name);
        return fallback != null ? fallback.clone() : new float[0];
    }

    boolean isDriven() {
        return this.size > 0 || this.floatBinding != null || this.vectorBinding != null || this.animationTo != null;
    }

    // Advances animations and evaluates bindings, once per frame while the effect is visible.
    void update(EffectContext context) {
        if (this.vectorBinding != null) {
            Arrays.fill(this.scratch, 0.0F);
            this.vectorBinding.get(context, this.scratch);
            this.store(this.scratch);
        } else if (this.floatBinding != null) {
            this.value[0] = this.floatBinding.get(context);
            this.size = Math.max(this.size, 1);
        } else if (this.animationTo != null && this.animationFrom != null) {
            this.animationElapsed += context.deltaTime();
            float progress = Math.min(1.0F, this.animationElapsed / this.animationDuration);
            float eased = this.animationEasing.apply(progress);
            for (int i = 0; i < this.animationTo.length && i < CAPACITY; i++) {
                this.value[i] = Mth.lerp(eased, this.animationFrom[i], this.animationTo[i]);
            }
            this.size = Math.max(this.size, Math.min(this.animationTo.length, CAPACITY));
            if (progress >= 1.0F) this.stopAnimation();
        }
    }

    /** Whether this uniform has its own value, rather than the definition's default. */
    boolean hasValue() {
        return this.size > 0;
    }

    /** The value's components, 16 floats with the unset ones 0; only meaningful while {@link #hasValue()}. */
    float[] values() {
        return this.value;
    }

    private void store(float[] values) {
        int count = Math.min(values.length, CAPACITY);
        System.arraycopy(values, 0, this.value, 0, count);
        Arrays.fill(this.value, count, CAPACITY, 0.0F);
        this.size = count;
    }

    private void stopAnimation() {
        this.animationFrom = null;
        this.animationTo = null;
    }
}
