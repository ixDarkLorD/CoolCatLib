package net.ixdarklord.coolcatcanvas.internal.client.sky;

import net.ixdarklord.coolcatcanvas.api.client.sky.SkyContext;
import net.ixdarklord.coolcatcanvas.api.client.sky.SkyLayer;
import net.ixdarklord.coolcatcanvas.api.client.sky.SkyLayerDefinition;
import net.ixdarklord.coolcatcanvas.api.client.sky.Skybox;
import net.ixdarklord.coolcatcanvas.api.client.sky.SkyboxDefinition;
import net.ixdarklord.coolcatcanvas.api.client.sky.VanillaSky;
import net.ixdarklord.coolcatcanvas.api.utils.Easing;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

// Visibility is the eased fade, which moves linearly in [0, 1]. What code sets overrides the definition, which is only
// known once loaded for JSON skyboxes, so each setting remembers whether code took it over.
final class SkyboxImpl implements Skybox {
    private final Identifier id;
    private final @Nullable Identifier definitionId;
    private final @Nullable SkyboxDefinition codeDefinition;
    private final boolean fromResources;

    private volatile int priority;
    private boolean priorityOverridden;
    private float fadeInSeconds = SkyboxDefinition.DEFAULT_FADE / 20.0F;
    private float fadeOutSeconds = SkyboxDefinition.DEFAULT_FADE / 20.0F;
    private Easing fadeEasing = Easing.SINE_IN_OUT;
    private boolean fadeOverridden;
    private @Nullable Predicate<SkyContext> condition;
    private boolean conditionOverridden;
    private @Nullable Consumer<SkyContext> frameCallback;
    private Set<VanillaSky> hidden = Set.of();
    private boolean hiddenOverridden;

    private boolean enabled;
    private float fade;
    // Seconds left of an enableFor, or negative when there's none.
    private float timeout = -1.0F;
    private float age;
    private float visibility;

    private final Map<String, SkyLayerImpl> layerHandles = new LinkedHashMap<>();
    private List<SkyLayer> loadedLayers = List.of();

    private @Nullable SkyboxProgram program;
    private @Nullable String error;
    private boolean needsLoad = true;

    SkyboxImpl(Identifier id, @Nullable Identifier definitionId, @Nullable SkyboxDefinition codeDefinition, boolean fromResources) {
        this.id = id;
        this.definitionId = definitionId;
        this.codeDefinition = codeDefinition;
        this.fromResources = fromResources;
        if (codeDefinition != null) this.applyDefaults(codeDefinition);
    }

    @Override
    public Identifier id() {
        return this.id;
    }

    @Override
    public @Nullable Identifier definitionId() {
        return this.definitionId;
    }

    boolean fromResources() {
        return this.fromResources;
    }

    @Override
    public int priority() {
        return this.priority;
    }

    @Override
    public Skybox priority(int priority) {
        this.priorityOverridden = true;
        this.setPriority(priority);
        return this;
    }

    @Override
    public Skybox fade(int fadeInTicks, int fadeOutTicks, Easing easing) {
        this.fadeOverridden = true;
        this.fadeInSeconds = Math.max(0, fadeInTicks) / 20.0F;
        this.fadeOutSeconds = Math.max(0, fadeOutTicks) / 20.0F;
        this.fadeEasing = Objects.requireNonNull(easing);
        return this;
    }

    @Override
    public Skybox activeWhen(@Nullable Predicate<SkyContext> condition) {
        this.conditionOverridden = true;
        this.condition = condition;
        return this;
    }

    @Override
    public Skybox onFrame(@Nullable Consumer<SkyContext> callback) {
        this.frameCallback = callback;
        return this;
    }

    @Override
    public Skybox hide(VanillaSky... parts) {
        this.hiddenOverridden = true;
        this.hidden = parts.length == 0 ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(List.of(parts)));
        return this;
    }

    @Override
    public Set<VanillaSky> hidden() {
        return this.hidden;
    }

    @Override
    public Skybox enable() {
        this.enabled = true;
        this.timeout = -1.0F;
        return this;
    }

    @Override
    public Skybox disable() {
        this.enabled = false;
        this.timeout = -1.0F;
        return this;
    }

    @Override
    public Skybox enableFor(int ticks) {
        this.enabled = true;
        this.timeout = Math.max(0, ticks) / 20.0F;
        return this;
    }

    @Override
    public Skybox enableInstantly() {
        this.enable();
        this.fade = 1.0F;
        this.visibility = 1.0F;
        return this;
    }

    @Override
    public Skybox disableInstantly() {
        this.disable();
        this.fade = 0.0F;
        this.visibility = 0.0F;
        this.age = 0.0F;
        return this;
    }

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    @Override
    public boolean isVisible() {
        return this.visibility > 0.0F;
    }

    @Override
    public float visibility() {
        return this.visibility;
    }

    @Override
    public SkyLayer layer(String name) {
        return this.handle(name);
    }

    @Override
    public Collection<SkyLayer> layers() {
        return this.loadedLayers;
    }

    @Override
    public boolean isLoaded() {
        return this.program != null && this.error == null;
    }

    @Override
    public @Nullable String error() {
        return this.error;
    }

    // ---- Driven by SkyboxManager, on the render thread ----

    @Nullable SkyboxProgram program() {
        return this.isLoaded() ? this.program : null;
    }

    boolean needsLoad() {
        return this.needsLoad;
    }

    void load(SkyboxManager.Loader loader) {
        this.unload();
        this.needsLoad = false;
        try {
            SkyboxDefinition definition = this.codeDefinition != null ? this.codeDefinition : loader.definition(Objects.requireNonNull(this.definitionId));
            this.applyDefaults(definition);
            this.layerHandles.values().forEach(handle -> handle.bind(null));
            this.program = SkyboxProgram.compile(definition, this);

            List<SkyLayer> layers = new ArrayList<>(definition.layers().size());
            for (SkyLayerDefinition layer : definition.layers()) layers.add(this.handle(layer.name()));
            this.loadedLayers = Collections.unmodifiableList(layers);
            for (String name : this.layerHandles.keySet()) {
                if (definition.layers().stream().noneMatch(layer -> layer.name().equals(name))) {
                    CoolCatCanvas.LOGGER.warn("Skybox {} has no layer named {}", this.id, name);
                }
            }
        } catch (Exception e) {
            this.error = e.getMessage() != null ? e.getMessage() : e.toString();
            CoolCatCanvas.LOGGER.error("Failed to load skybox {}", this.id, e);
        }
    }

    void unload() {
        this.program = null;
        this.loadedLayers = List.of();
        this.error = null;
    }

    /** Stops drawing until the next resource reload. */
    void fail(String message, Throwable cause) {
        if (this.error != null) return;
        this.error = message + ": " + cause;
        CoolCatCanvas.LOGGER.error("Skybox {} stopped: {}", this.id, message, cause);
    }

    void update(SkyboxManager.Frame frame) {
        Predicate<SkyContext> condition = this.condition;
        if (condition != null) {
            boolean wanted = condition.test(frame.context(this, this.age, this.visibility));
            if (wanted != this.enabled) {
                this.enabled = wanted;
                this.timeout = -1.0F;
            }
        }

        float delta = frame.deltaTime();
        if (this.enabled && this.timeout >= 0.0F) {
            this.timeout -= delta;
            if (this.timeout <= 0.0F) this.disable();
        }
        if (this.enabled) this.fade = this.fadeInSeconds <= 0.0F ? 1.0F : Math.min(1.0F, this.fade + delta / this.fadeInSeconds);
        else this.fade = this.fadeOutSeconds <= 0.0F ? 0.0F : Math.max(0.0F, this.fade - delta / this.fadeOutSeconds);

        this.visibility = this.fadeEasing.apply(this.fade);
        if (this.visibility <= 0.0F) {
            this.visibility = 0.0F;
            this.age = 0.0F;
            return;
        }

        this.age += delta;
        SkyContext context = frame.context(this, this.age, this.visibility);
        if (this.frameCallback != null) this.frameCallback.accept(context);
        for (SkyLayerImpl layer : this.layerHandles.values()) layer.update(context);
    }

    SkyLayerImpl handle(String name) {
        return this.layerHandles.computeIfAbsent(name, SkyLayerImpl::new);
    }

    private void applyDefaults(SkyboxDefinition definition) {
        if (!this.priorityOverridden) this.setPriority(definition.priority());
        if (!this.fadeOverridden) {
            this.fadeInSeconds = definition.fadeIn() / 20.0F;
            this.fadeOutSeconds = definition.fadeOut() / 20.0F;
        }
        if (!this.hiddenOverridden) this.hidden = definition.hide();
        if (!this.conditionOverridden) {
            List<ResourceKey<Level>> dimensions = definition.dimensions();
            this.condition = dimensions.isEmpty() ? null : context -> context.level() != null && dimensions.contains(context.level().dimension());
        }
    }

    private void setPriority(int priority) {
        if (this.priority != priority) {
            this.priority = priority;
            SkyboxManager.markOrderDirty();
        }
    }
}
