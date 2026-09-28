package net.ixdarklord.coolcatcanvas.internal.client.effect;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.serialization.JsonOps;
import net.ixdarklord.coolcatcanvas.api.client.effect.EffectContext;
import net.ixdarklord.coolcatcanvas.api.client.effect.ScreenEffect;
import net.ixdarklord.coolcatcanvas.api.client.effect.ScreenEffectDefinition;
import net.ixdarklord.coolcatcanvas.api.client.effect.ScreenEffectLayers;
import net.ixdarklord.coolcatcanvas.api.client.effect.ScreenEffectStage;
import net.ixdarklord.coolcatcanvas.api.event.v2.client.ScreenEffectEvents;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs screen effects: GameRenderer's mixin calls {@link #beginFrame}, {@link #render} per stage and
 * {@link #endFrame}; ShaderManager's calls {@link #reload} once shaders are (re)loaded.
 */
public final class ScreenEffectManager {
    // Registration may come from mod construction threads; everything else runs on the render thread.
    private static final Map<Identifier, ScreenEffectImpl> EFFECTS = new ConcurrentHashMap<>();
    private static final ScreenEffectLayersImpl LAYERS = new ScreenEffectLayersImpl();
    private static final AtomicInteger SERIALS = new AtomicInteger();
    private static final Map<String, RenderPipeline> PIPELINES = new HashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static final long START_NANOS = Util.getNanos();

    private static @Nullable CrossFrameResourcePool resourcePool;
    private static boolean resourcesLoaded;
    private static boolean firstLoadDone;
    private static long lastFrameNanos = -1L;
    private static EffectProgram.FrameInfo frameInfo = new EffectProgram.FrameInfo(0.0F, 0.0F);
    private static ScreenEffectEvents.ToggleCause cause = ScreenEffectEvents.ToggleCause.CODE;
    // While the effects screen is open, SCREEN effects are drawn under the GUI so it stays readable.
    private static boolean previewUnderGui;

    private ScreenEffectManager() {}

    public static ScreenEffect register(Identifier id, @Nullable Identifier definitionId, @Nullable ScreenEffectDefinition definition) {
        ScreenEffectImpl effect = new ScreenEffectImpl(id, definitionId, definition, SERIALS.getAndIncrement());
        if (EFFECTS.putIfAbsent(id, effect) != null) throw new IllegalArgumentException("Screen effect " + id + " is already registered");
        LAYERS.add(effect);
        // Registered after the saved choices were applied: apply them now, once its configuration is chained on.
        if (resourcesLoaded) Minecraft.getInstance().execute(() -> ScreenEffectPreferences.apply(effect));
        return effect;
    }

    public static @Nullable ScreenEffect get(Identifier id) {
        return EFFECTS.get(id);
    }

    public static Collection<ScreenEffect> all() {
        return Collections.unmodifiableCollection(EFFECTS.values());
    }

    public static boolean unregister(Identifier id) {
        RenderSystem.assertOnRenderThread();
        ScreenEffectImpl effect = EFFECTS.remove(id);
        if (effect == null) return false;
        LAYERS.remove(effect);
        effect.unload();
        return true;
    }

    public static ScreenEffectLayers layers() {
        return LAYERS;
    }

    static ScreenEffectLayersImpl layersImpl() {
        return LAYERS;
    }

    /** What toggles made now are attributed to. */
    static ScreenEffectEvents.ToggleCause cause() {
        return cause;
    }

    /** Runs code whose toggles are attributed to {@code cause}. */
    public static void runAs(ScreenEffectEvents.ToggleCause newCause, Runnable action) {
        ScreenEffectEvents.ToggleCause previous = cause;
        cause = newCause;
        try {
            action.run();
        } finally {
            cause = previous;
        }
    }

    public static void setPreviewUnderGui(boolean preview) {
        previewUnderGui = preview;
    }

    // ---- Frame ----

    public static void beginFrame(DeltaTracker deltaTracker) {
        long now = Util.getNanos();
        float delta = lastFrameNanos < 0L ? 0.0F : Math.min(1.0F, (now - lastFrameNanos) / 1.0E9F);
        lastFrameNanos = now;
        float time = (float) (((now - START_NANOS) / 1.0E9D) % 3600.0D);
        frameInfo = new EffectProgram.FrameInfo(time, RANDOM.nextFloat());
        if (!resourcesLoaded || EFFECTS.isEmpty()) return;

        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget main = minecraft.getMainRenderTarget();
        Frame frame = new Frame(minecraft, deltaTracker.getGameTimeDeltaPartialTick(false), time, delta, main.width, main.height);
        Loader loader = new Loader(minecraft.getResourceManager());
        for (ScreenEffectImpl effect : EFFECTS.values()) {
            effect.freeIfFailed();
            if (effect.needsLoad()) effect.load(loader);
            if (effect.error() != null) continue;
            try {
                effect.update(frame);
            } catch (RuntimeException e) {
                effect.fail("A callback threw", e);
                effect.forceOff();
            }
        }
    }

    public static void render(ScreenEffectStage stage) {
        if (!resourcesLoaded || EFFECTS.isEmpty()) return;
        if (previewUnderGui && stage == ScreenEffectStage.SCREEN) return;
        List<ScreenEffectImpl> active = new ArrayList<>();
        // In layer order, stage by stage: while previewing, the SCREEN effects follow the WORLD ones.
        for (ScreenEffectStage drawn : ScreenEffectStage.values()) {
            if (drawn != stage && !(previewUnderGui && stage == ScreenEffectStage.WORLD)) continue;
            for (ScreenEffectImpl effect : LAYERS.view()) {
                if (effect.stage() == drawn && effect.isVisible() && effect.program() != null) active.add(effect);
            }
        }
        if (active.isEmpty()) return;

        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        FrameGraphBuilder frame = new FrameGraphBuilder();
        ResourceHandle<RenderTarget> handle = frame.importExternal("main", main);
        for (ScreenEffectImpl effect : active) {
            handle = effect.program().addToFrame(frame, handle, main.width, main.height, effect, frameInfo);
        }
        frame.execute(resourcePool());
    }

    public static void endFrame() {
        if (resourcePool != null) resourcePool.endFrame();
    }

    public static void onResize() {
        if (resourcePool != null) resourcePool.clear();
    }

    // ---- Resources ----

    /** After shaders load: rebuilds every effect from the new resources. */
    public static void reload(ResourceManager resourceManager) {
        RenderSystem.assertOnRenderThread();
        resourcesLoaded = true;
        PIPELINES.clear();
        Loader loader = new Loader(resourceManager);
        for (ScreenEffectImpl effect : EFFECTS.values()) effect.load(loader);
        if (!firstLoadDone) {
            firstLoadDone = true;
            ScreenEffectPreferences.load();
            List<Identifier> order = ScreenEffectPreferences.savedOrder();
            if (order != null) LAYERS.restore(order);
            for (ScreenEffectImpl effect : EFFECTS.values()) ScreenEffectPreferences.apply(effect);
        }
    }

    public static void close() {
        for (ScreenEffectImpl effect : EFFECTS.values()) effect.unload();
        PIPELINES.clear();
        if (resourcePool != null) {
            resourcePool.close();
            resourcePool = null;
        }
    }

    private static CrossFrameResourcePool resourcePool() {
        if (resourcePool == null) resourcePool = new CrossFrameResourcePool(3);
        return resourcePool;
    }

    /** One frame's shared values, turned into each effect's context. */
    record Frame(Minecraft minecraft, float partialTick, float time, float deltaTime, int width, int height) {
        EffectContext context(ScreenEffectImpl effect, float age, float strength) {
            return new EffectContext(effect, this.minecraft.player, this.minecraft.level, this.partialTick, this.time, this.deltaTime, age, strength, this.width, this.height);
        }
    }

    /** Reads definitions and compiles pipelines for effects being loaded. */
    static final class Loader {
        private final ResourceManager resourceManager;
        private final Map<Identifier, ScreenEffectDefinition> definitions = new HashMap<>();

        private Loader(ResourceManager resourceManager) {
            this.resourceManager = resourceManager;
        }

        ScreenEffectDefinition definition(Identifier id) throws IOException {
            ScreenEffectDefinition cached = this.definitions.get(id);
            if (cached != null) return cached;
            Identifier location = id.withPath(path -> "post_effect/" + path + ".json");
            Optional<Resource> resource = this.resourceManager.getResource(location);
            if (resource.isEmpty()) throw new IOException("Missing screen effect definition " + location);
            try (Reader reader = resource.get().openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                ScreenEffectDefinition definition = ScreenEffectDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                        .getOrThrow(message -> new IOException("Invalid screen effect definition " + location + ": " + message));
                this.definitions.put(id, definition);
                return definition;
            }
        }

        EffectProgram compile(Identifier effectId, ScreenEffectDefinition definition, boolean autoBlend) throws ShaderManager.CompilationException {
            return EffectProgram.compile(effectId, definition, autoBlend, Loader::pipeline);
        }

        private static RenderPipeline pipeline(EffectProgram.PipelineKey key) {
            RenderPipeline cached = PIPELINES.get(key.cacheKey());
            if (cached != null) return cached;
            RenderPipeline pipeline = key.build();
            CompiledRenderPipeline compiled = RenderSystem.getDevice().precompilePipeline(pipeline);
            if (!compiled.isValid()) {
                throw new IllegalStateException("Shaders " + key.vertexShader() + " / " + key.fragmentShader() + " failed to compile or link (see the log above)");
            }
            PIPELINES.put(key.cacheKey(), pipeline);
            CoolCatCanvas.LOGGER.debug("Compiled screen effect pipeline {}", key.cacheKey());
            return pipeline;
        }
    }
}
