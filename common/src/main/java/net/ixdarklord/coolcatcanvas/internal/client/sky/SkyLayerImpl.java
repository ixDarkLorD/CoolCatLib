package net.ixdarklord.coolcatcanvas.internal.client.sky;

import net.ixdarklord.coolcatcanvas.api.client.sky.SkyContext;
import net.ixdarklord.coolcatcanvas.api.client.sky.SkyLayer;
import net.ixdarklord.coolcatcanvas.api.client.sky.SkyLayerDefinition;
import net.ixdarklord.coolcatcanvas.api.utils.Easing;
import net.minecraft.util.Mth;
import org.joml.Vector4fc;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

// The runtime overrides of one named layer. Values the code never touched fall back to the definition's, so a reload
// that changes the definition shows through.
final class SkyLayerImpl implements SkyLayer {
    private static final int PARAMS = SkyLayerDefinition.MAX_PARAMS;

    private final String name;
    private boolean visible = true;
    private final Tween alpha = new Tween(1.0F);
    private final Tween tint = new Tween(1.0F, 1.0F, 1.0F, 1.0F);
    private boolean tintSet;
    private final Tween[] params = new Tween[PARAMS];
    private final boolean[] paramSet = new boolean[PARAMS];
    private final ParamBinding[] bindings = new ParamBinding[PARAMS];
    private final float[] scratch = new float[4];

    // The definition this handle was last matched with, for defaults; null while its skybox lacks such a layer.
    private @Nullable SkyLayerDefinition definition;

    SkyLayerImpl(String name) {
        this.name = name;
        for (int i = 0; i < PARAMS; i++) this.params[i] = new Tween(0.0F, 0.0F, 0.0F, 0.0F);
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public SkyLayer setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    @Override
    public boolean isVisible() {
        return this.visible;
    }

    @Override
    public SkyLayer setAlpha(float alpha) {
        this.alpha.set(Mth.clamp(alpha, 0.0F, 1.0F));
        return this;
    }

    @Override
    public SkyLayer animateAlpha(float target, int ticks, Easing easing) {
        this.alpha.animateTo(ticks, Objects.requireNonNull(easing), Mth.clamp(target, 0.0F, 1.0F));
        return this;
    }

    @Override
    public float alpha() {
        return this.alpha.value[0];
    }

    @Override
    public SkyLayer setTint(int argb) {
        this.tintSet = true;
        this.tint.set(rgba(argb));
        return this;
    }

    @Override
    public SkyLayer animateTint(int argb, int ticks, Easing easing) {
        if (!this.tintSet) {
            this.tint.set(rgba(this.definitionTint()));
            this.tintSet = true;
        }
        this.tint.animateTo(ticks, Objects.requireNonNull(easing), rgba(argb));
        return this;
    }

    @Override
    public SkyLayer resetTint() {
        this.tintSet = false;
        return this;
    }

    @Override
    public SkyLayer setParam(int index, float... values) {
        checkIndex(index);
        this.bindings[index] = null;
        this.paramSet[index] = true;
        this.params[index].set(values);
        return this;
    }

    @Override
    public SkyLayer animateParam(int index, int ticks, Easing easing, float... target) {
        checkIndex(index);
        this.bindings[index] = null;
        if (!this.paramSet[index]) {
            this.params[index].set(this.definitionParam(index));
            this.paramSet[index] = true;
        }
        this.params[index].animateTo(ticks, Objects.requireNonNull(easing), target);
        return this;
    }

    @Override
    public SkyLayer bindParam(int index, ParamBinding binding) {
        checkIndex(index);
        this.bindings[index] = Objects.requireNonNull(binding);
        this.paramSet[index] = true;
        return this;
    }

    @Override
    public SkyLayer resetParams() {
        Arrays.fill(this.bindings, null);
        Arrays.fill(this.paramSet, false);
        return this;
    }

    @Override
    public float[] param(int index) {
        checkIndex(index);
        return this.paramSet[index] ? this.params[index].value.clone() : this.definitionParam(index);
    }

    // ---- Driven by the skybox, on the render thread ----

    void bind(@Nullable SkyLayerDefinition definition) {
        this.definition = definition;
    }

    /** Advances animations and evaluates bindings, once per frame while the skybox is visible. */
    void update(SkyContext context) {
        float delta = context.deltaTime();
        this.alpha.update(delta);
        this.tint.update(delta);
        for (int i = 0; i < PARAMS; i++) {
            ParamBinding binding = this.bindings[i];
            if (binding != null) {
                Arrays.fill(this.scratch, 0.0F);
                binding.get(context, this.scratch);
                this.params[i].set(this.scratch);
            } else {
                this.params[i].update(delta);
            }
        }
    }

    /** The tint as RGBA, into {@code out}. */
    void tint(float[] out) {
        if (this.tintSet) System.arraycopy(this.tint.value, 0, out, 0, 4);
        else rgba(this.definitionTint(), out);
    }

    /** Param {@code index} as 4 floats, into {@code out} at {@code offset}. */
    void param(int index, float[] out, int offset) {
        if (this.paramSet[index]) {
            System.arraycopy(this.params[index].value, 0, out, offset, 4);
        } else if (this.definition != null) {
            Vector4fc param = this.definition.param(index);
            out[offset] = param.x();
            out[offset + 1] = param.y();
            out[offset + 2] = param.z();
            out[offset + 3] = param.w();
        } else {
            Arrays.fill(out, offset, offset + 4, 0.0F);
        }
    }

    private int definitionTint() {
        return this.definition != null ? this.definition.tint() : SkyLayerDefinition.WHITE;
    }

    private float[] definitionParam(int index) {
        float[] values = new float[4];
        this.param(index, values, 0);
        return values;
    }

    private static float[] rgba(int argb) {
        float[] out = new float[4];
        rgba(argb, out);
        return out;
    }

    private static void rgba(int argb, float[] out) {
        out[0] = (argb >> 16 & 0xFF) / 255.0F;
        out[1] = (argb >> 8 & 0xFF) / 255.0F;
        out[2] = (argb & 0xFF) / 255.0F;
        out[3] = (argb >>> 24) / 255.0F;
    }

    private static void checkIndex(int index) {
        if (index < 0 || index >= PARAMS) throw new IndexOutOfBoundsException("Sky layer params go from 0 to " + (PARAMS - 1) + ", got " + index);
    }
}
