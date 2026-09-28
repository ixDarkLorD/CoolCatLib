package net.ixdarklord.coolcatcanvas.internal.client.effect;

import net.ixdarklord.coolcatcanvas.api.client.effect.EffectContext;
import net.ixdarklord.coolcatcanvas.api.client.effect.EffectUniform;
import net.ixdarklord.coolcatcanvas.api.client.effect.ScreenEffect;
import net.ixdarklord.coolcatcanvas.api.client.effect.ScreenEffectDefinition;
import net.ixdarklord.coolcatcanvas.api.client.effect.ScreenEffectStage;
import net.ixdarklord.coolcatcanvas.api.event.v2.client.ScreenEffectEvents;
import net.ixdarklord.coolcatcanvas.api.utils.Easing;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.client.renderer.UniformValue;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

// Strength = eased fade * manual strength * strength function. The fade moves linearly in [0, 1] and is eased on read.
public final class ScreenEffectImpl implements ScreenEffect {
    private final Identifier id;
    private final @Nullable Identifier definitionId;
    private final @Nullable ScreenEffectDefinition codeDefinition;

    // Registration order, breaking ties between equal priorities.
    final int serial;
    private Component displayName;
    private @Nullable Component description;
    private boolean selectable = true;

    private volatile ScreenEffectStage stage = ScreenEffectStage.WORLD;
    private volatile int priority;
    private float fadeInSeconds = 0.5F;
    private float fadeOutSeconds = 0.5F;
    private Easing fadeEasing = Easing.SINE_IN_OUT;
    private boolean autoBlend;
    private @Nullable Predicate<EffectContext> condition;
    private EffectContext.@Nullable StrengthFunction strengthFunction;
    private @Nullable Consumer<EffectContext> frameCallback;

    private boolean enabled;
    private @Nullable Boolean lastCondition;
    private float fade;
    // Seconds left of an enableFor, or negative when there's none.
    private float timeout = -1.0F;
    private float manualStrength = 1.0F;
    private float manualFrom;
    private float manualTo = 1.0F;
    private float manualElapsed;
    private float manualDuration;
    private Easing manualEasing = Easing.LINEAR;
    private float age;
    private float strength;

    private final Map<String, EffectUniformImpl> uniforms = new LinkedHashMap<>();
    private Map<String, float[]> defaults = Map.of();

    private @Nullable EffectProgram program;
    private @Nullable String error;
    private boolean needsLoad = true;

    ScreenEffectImpl(Identifier id, @Nullable Identifier definitionId, @Nullable ScreenEffectDefinition codeDefinition, int serial) {
        this.id = id;
        this.definitionId = definitionId;
        this.codeDefinition = codeDefinition;
        this.serial = serial;
        this.displayName = Component.translatableWithFallback(id.toLanguageKey("screen_effect"), defaultName(id));
    }

    // "my_mod:fx/heat_haze" -> "Heat Haze"
    private static String defaultName(Identifier id) {
        String path = id.getPath().substring(id.getPath().lastIndexOf('/') + 1);
        StringBuilder name = new StringBuilder();
        for (String word : path.split("_")) {
            if (word.isEmpty()) continue;
            if (!name.isEmpty()) name.append(' ');
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name.toString();
    }

    @Override
    public Identifier id() {
        return this.id;
    }

    @Override
    public @Nullable Identifier definitionId() {
        return this.definitionId;
    }

    @Override
    public Component displayName() {
        return this.displayName;
    }

    @Override
    public ScreenEffect displayName(Component name) {
        this.displayName = Objects.requireNonNull(name);
        return this;
    }

    @Override
    public @Nullable Component description() {
        return this.description;
    }

    @Override
    public ScreenEffect description(@Nullable Component description) {
        this.description = description;
        return this;
    }

    @Override
    public boolean isSelectable() {
        return this.selectable;
    }

    @Override
    public ScreenEffect selectable(boolean selectable) {
        this.selectable = selectable;
        return this;
    }

    @Override
    public boolean isAutomatic() {
        return this.condition != null;
    }

    @Override
    public ScreenEffectStage stage() {
        return this.stage;
    }

    @Override
    public ScreenEffect stage(ScreenEffectStage stage) {
        this.stage = Objects.requireNonNull(stage);
        return this;
    }

    @Override
    public int priority() {
        return this.priority;
    }

    @Override
    public ScreenEffect priority(int priority) {
        if (this.priority != priority) {
            this.priority = priority;
            ScreenEffectManager.layersImpl().onPriorityChanged();
        }
        return this;
    }

    @Override
    public ScreenEffect fade(int fadeInTicks, int fadeOutTicks, Easing easing) {
        this.fadeInSeconds = Math.max(0, fadeInTicks) / 20.0F;
        this.fadeOutSeconds = Math.max(0, fadeOutTicks) / 20.0F;
        this.fadeEasing = Objects.requireNonNull(easing);
        return this;
    }

    @Override
    public ScreenEffect autoBlend(boolean autoBlend) {
        if (this.autoBlend != autoBlend) {
            this.autoBlend = autoBlend;
            this.needsLoad = true;
        }
        return this;
    }

    @Override
    public ScreenEffect activeWhen(@Nullable Predicate<EffectContext> condition) {
        this.condition = condition;
        this.lastCondition = null;
        return this;
    }

    @Override
    public ScreenEffect strength(EffectContext.@Nullable StrengthFunction function) {
        this.strengthFunction = function;
        return this;
    }

    @Override
    public ScreenEffect onFrame(@Nullable Consumer<EffectContext> callback) {
        this.frameCallback = callback;
        return this;
    }

    @Override
    public ScreenEffect enable() {
        if (this.toggle(true, ScreenEffectManager.cause())) this.timeout = -1.0F;
        return this;
    }

    @Override
    public ScreenEffect disable() {
        if (this.toggle(false, ScreenEffectManager.cause())) this.timeout = -1.0F;
        return this;
    }

    @Override
    public ScreenEffect enableFor(int ticks) {
        if (this.toggle(true, ScreenEffectManager.cause())) this.timeout = Math.max(0, ticks) / 20.0F;
        return this;
    }

    @Override
    public ScreenEffect enableInstantly() {
        if (this.toggle(true, ScreenEffectManager.cause())) {
            this.timeout = -1.0F;
            this.fade = 1.0F;
        }
        return this;
    }

    @Override
    public ScreenEffect disableInstantly() {
        if (this.toggle(false, ScreenEffectManager.cause())) this.hide();
        return this;
    }

    // Turns on or off unless a BEFORE_TOGGLE listener objects; true if it's now in the wanted state.
    private boolean toggle(boolean enabled, ScreenEffectEvents.ToggleCause cause) {
        if (this.enabled == enabled) return true;
        if (ScreenEffectEvents.BEFORE_TOGGLE.invoker().onToggle(this, enabled, cause).isInterrupt()) return false;
        this.enabled = enabled;
        ScreenEffectEvents.TOGGLED.invoker().onToggled(this, enabled, cause);
        return true;
    }

    private void hide() {
        this.timeout = -1.0F;
        this.fade = 0.0F;
        this.strength = 0.0F;
        this.age = 0.0F;
    }

    /** Off at once whatever listeners say, for an effect that broke. */
    void forceOff() {
        boolean wasEnabled = this.enabled;
        this.enabled = false;
        this.hide();
        if (wasEnabled) ScreenEffectEvents.TOGGLED.invoker().onToggled(this, false, ScreenEffectEvents.ToggleCause.CODE);
    }

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    @Override
    public boolean isVisible() {
        return this.strength > 0.0F;
    }

    @Override
    public ScreenEffect setStrength(float strength) {
        float old = this.manualStrength;
        this.manualStrength = Mth.clamp(strength, 0.0F, 1.0F);
        this.manualTo = this.manualStrength;
        this.manualDuration = 0.0F;
        if (old != this.manualStrength) ScreenEffectEvents.STRENGTH_CHANGED.invoker().onStrengthChanged(this, old, this.manualStrength);
        return this;
    }

    @Override
    public ScreenEffect animateStrength(float target, int ticks, Easing easing) {
        if (ticks <= 0) return this.setStrength(target);
        this.manualFrom = this.manualStrength;
        this.manualTo = Mth.clamp(target, 0.0F, 1.0F);
        this.manualElapsed = 0.0F;
        this.manualDuration = ticks / 20.0F;
        this.manualEasing = Objects.requireNonNull(easing);
        ScreenEffectEvents.STRENGTH_CHANGED.invoker().onStrengthChanged(this, this.manualFrom, this.manualTo);
        return this;
    }

    /** The manual strength, or where its animation is heading. */
    public float manualStrength() {
        return this.manualDuration > 0.0F ? this.manualTo : this.manualStrength;
    }

    @Override
    public float strength() {
        return this.strength;
    }

    float age() {
        return this.age;
    }

    @Override
    public EffectUniform uniform(String name) {
        return this.uniforms.computeIfAbsent(name, key -> new EffectUniformImpl(this, key));
    }

    @Override
    public ScreenEffect resetUniforms() {
        this.uniforms.values().forEach(EffectUniformImpl::reset);
        return this;
    }

    @Nullable EffectUniformImpl uniformIfSet(String name) {
        EffectUniformImpl uniform = this.uniforms.get(name);
        return uniform != null && uniform.hasValue() ? uniform : null;
    }

    float @Nullable [] defaultValue(String name) {
        return this.defaults.get(name);
    }

    @Override
    public boolean isLoaded() {
        return this.program != null && this.error == null;
    }

    @Override
    public @Nullable String error() {
        return this.error;
    }

    @Nullable EffectProgram program() {
        return this.isLoaded() ? this.program : null;
    }

    // ---- Driven by ScreenEffectManager, on the render thread ----

    boolean needsLoad() {
        return this.needsLoad;
    }

    void markForReload() {
        this.needsLoad = true;
    }

    void load(ScreenEffectManager.Loader loader) {
        this.unload();
        this.needsLoad = false;
        try {
            ScreenEffectDefinition definition = this.codeDefinition != null ? this.codeDefinition : loader.definition(Objects.requireNonNull(this.definitionId));
            this.program = loader.compile(this.id, definition, this.autoBlend);
            this.defaults = defaultsOf(definition);
            for (EffectUniformImpl uniform : this.uniforms.values()) {
                if (uniform.isDriven() && !this.defaults.containsKey(uniform.name())) {
                    CoolCatCanvas.LOGGER.warn("Screen effect {} sets uniform {}, which none of its passes declare", this.id, uniform.name());
                }
            }
        } catch (Exception e) {
            this.error = e.getMessage() != null ? e.getMessage() : e.toString();
            CoolCatCanvas.LOGGER.error("Failed to load screen effect {}", this.id, e);
        }
        ScreenEffectEvents.LOADED.invoker().onLoaded(this, this.error);
    }

    void unload() {
        if (this.program != null) {
            this.program.close();
            this.program = null;
        }
        this.error = null;
    }

    /** Stops drawing until the next resource reload. Safe mid-frame: the program is freed on the next update. */
    void fail(String message, Throwable cause) {
        if (this.error != null) return;
        this.error = message + ": " + cause;
        CoolCatCanvas.LOGGER.error("Screen effect {} stopped: {}", this.id, message, cause);
    }

    void freeIfFailed() {
        if (this.error != null && this.program != null) {
            this.program.close();
            this.program = null;
        }
    }

    void update(ScreenEffectManager.Frame frame) {
        EffectContext context = frame.context(this, this.age, this.baseStrength());
        // The condition acts when it changes, so a veto or a manual toggle holds until it changes again.
        if (this.condition != null) {
            boolean wanted = this.condition.test(context);
            if (this.lastCondition == null || wanted != this.lastCondition) {
                this.lastCondition = wanted;
                if (this.toggle(wanted, ScreenEffectEvents.ToggleCause.CONDITION)) this.timeout = -1.0F;
            }
        }

        float delta = frame.deltaTime();
        if (this.enabled && this.timeout >= 0.0F) {
            this.timeout -= delta;
            if (this.timeout <= 0.0F) {
                this.timeout = -1.0F;
                this.toggle(false, ScreenEffectEvents.ToggleCause.TIMEOUT);
            }
        }
        if (this.enabled) this.fade = this.fadeInSeconds <= 0.0F ? 1.0F : Math.min(1.0F, this.fade + delta / this.fadeInSeconds);
        else this.fade = this.fadeOutSeconds <= 0.0F ? 0.0F : Math.max(0.0F, this.fade - delta / this.fadeOutSeconds);

        if (this.manualDuration > 0.0F) {
            this.manualElapsed += delta;
            float progress = Math.min(1.0F, this.manualElapsed / this.manualDuration);
            this.manualStrength = Mth.lerp(this.manualEasing.apply(progress), this.manualFrom, this.manualTo);
            if (progress >= 1.0F) this.manualDuration = 0.0F;
        }

        float base = this.baseStrength();
        if (base <= 0.0F) {
            this.strength = 0.0F;
            this.age = 0.0F;
            return;
        }

        this.age += delta;
        context = frame.context(this, this.age, base);
        float scale = this.strengthFunction != null ? Mth.clamp(this.strengthFunction.strength(context), 0.0F, 1.0F) : 1.0F;
        this.strength = base * scale;
        if (this.strength <= 0.0F) return;

        if (this.frameCallback != null) this.frameCallback.accept(context);
        for (EffectUniformImpl uniform : this.uniforms.values()) uniform.update(context);
    }

    private float baseStrength() {
        return this.fadeEasing.apply(this.fade) * this.manualStrength;
    }

    private static Map<String, float[]> defaultsOf(ScreenEffectDefinition definition) {
        Map<String, float[]> defaults = new HashMap<>();
        for (ScreenEffectDefinition.Pass pass : definition.passes()) {
            for (var block : pass.uniforms().values()) {
                for (ScreenEffectDefinition.UniformSpec spec : block) defaults.putIfAbsent(spec.name(), components(spec.value()));
            }
        }
        return defaults;
    }

    private static float[] components(UniformValue value) {
        return switch (value) {
            case UniformValue.FloatUniform(float v) -> new float[]{v};
            case UniformValue.IntUniform(int v) -> new float[]{v};
            case UniformValue.Vec2Uniform(var v) -> new float[]{v.x(), v.y()};
            case UniformValue.Vec3Uniform(var v) -> new float[]{v.x(), v.y(), v.z()};
            case UniformValue.Vec4Uniform(var v) -> new float[]{v.x(), v.y(), v.z(), v.w()};
            case UniformValue.IVec3Uniform(var v) -> new float[]{v.x(), v.y(), v.z()};
            case UniformValue.Matrix4x4Uniform(var v) -> v.get(new float[16]);
            default -> new float[0];
        };
    }
}
