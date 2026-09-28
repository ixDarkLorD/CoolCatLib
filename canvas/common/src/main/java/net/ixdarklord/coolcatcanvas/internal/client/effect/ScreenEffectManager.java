package net.ixdarklord.coolcatcanvas.internal.client.effect;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.serialization.JsonOps;
import net.ixdarklord.coolcatcanvas.api.client.effect.EffectContext;
import net.ixdarklord.coolcatcanvas.api.client.effect.ScreenEffect;
import net.ixdarklord.coolcatcanvas.api.client.effect.ScreenEffectDefinition;
import net.ixdarklord.coolcatcanvas.api.client.effect.ScreenEffectLayers;
import net.ixdarklord.coolcatcanvas.api.client.effect.ScreenEffectStage;
import net.ixdarklord.coolcatcanvas.api.event.v2.client.ScreenEffectEvents;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

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
 * {@link #endFrame}, and {@link #reload} once shaders are (re)loaded.
 */
public final class ScreenEffectManager {
    private static final List<String> ATTRIBUTES = List.of("Position");
    // Registration may come from mod construction threads; everything else runs on the render thread.
    private static final Map<ResourceLocation, ScreenEffectImpl> EFFECTS = new ConcurrentHashMap<>();
    private static final ScreenEffectLayersImpl LAYERS = new ScreenEffectLayersImpl();
    private static final AtomicInteger SERIALS = new AtomicInteger();
    private static final Map<String, ShaderProgram> PROGRAMS = new HashMap<>();
    private static final TargetPool TARGETS = new TargetPool(3);
    private static final RandomSource RANDOM = RandomSource.create();
    private static final long START_NANOS = Util.getNanos();

    private static boolean resourcesLoaded;
    private static boolean firstLoadDone;
    private static long lastFrameNanos = -1L;
    private static EffectProgram.FrameInfo frameInfo = new EffectProgram.FrameInfo(0.0F, 0.0F);
    private static ScreenEffectEvents.ToggleCause cause = ScreenEffectEvents.ToggleCause.CODE;
    // While the effects screen is open, SCREEN effects are drawn under the GUI so it stays readable.
    private static boolean previewUnderGui;

    private ScreenEffectManager() {}

    public static ScreenEffect register(ResourceLocation id, @Nullable ResourceLocation definitionId, @Nullable ScreenEffectDefinition definition) {
        ScreenEffectImpl effect = new ScreenEffectImpl(id, definitionId, definition, SERIALS.getAndIncrement());
        if (EFFECTS.putIfAbsent(id, effect) != null) throw new IllegalArgumentException("Screen effect " + id + " is already registered");
        LAYERS.add(effect);
        // Registered after the saved choices were applied: apply them now, once its configuration is chained on.
        if (resourcesLoaded) Minecraft.getInstance().execute(() -> ScreenEffectPreferences.apply(effect));
        return effect;
    }

    public static @Nullable ScreenEffect get(ResourceLocation id) {
        return EFFECTS.get(id);
    }

    public static Collection<ScreenEffect> all() {
        return Collections.unmodifiableCollection(EFFECTS.values());
    }

    public static boolean unregister(ResourceLocation id) {
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
        // As vanilla's post chains draw: no blending, no depth.
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        try {
            for (ScreenEffectImpl effect : active) {
                EffectProgram program = effect.program();
                if (program != null) program.draw(main, effect, frameInfo, TARGETS);
            }
        } finally {
            ShaderProgram.release();
            RenderSystem.activeTexture(33984);
            main.setFilterMode(9728);
            main.bindWrite(true);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    public static void endFrame() {
        TARGETS.endFrame();
    }

    public static void onResize() {
        TARGETS.clear();
    }

    // ---- Resources ----

    /** After shaders load: rebuilds every effect from the new resources. */
    public static void reload(ResourceManager resourceManager) {
        RenderSystem.assertOnRenderThread();
        resourcesLoaded = true;
        for (ScreenEffectImpl effect : EFFECTS.values()) effect.unload();
        closePrograms();
        Loader loader = new Loader(resourceManager);
        for (ScreenEffectImpl effect : EFFECTS.values()) effect.load(loader);
        if (!firstLoadDone) {
            firstLoadDone = true;
            ScreenEffectPreferences.load();
            List<ResourceLocation> order = ScreenEffectPreferences.savedOrder();
            if (order != null) LAYERS.restore(order);
            for (ScreenEffectImpl effect : EFFECTS.values()) ScreenEffectPreferences.apply(effect);
        }
    }

    public static void close() {
        for (ScreenEffectImpl effect : EFFECTS.values()) effect.unload();
        closePrograms();
        TARGETS.close();
    }

    private static void closePrograms() {
        PROGRAMS.values().forEach(ShaderProgram::close);
        PROGRAMS.clear();
    }

    /** One frame's shared values, turned into each effect's context. */
    record Frame(Minecraft minecraft, float partialTick, float time, float deltaTime, int width, int height) {
        EffectContext context(ScreenEffectImpl effect, float age, float strength) {
            return new EffectContext(effect, this.minecraft.player, this.minecraft.level, this.partialTick, this.time, this.deltaTime, age, strength, this.width, this.height);
        }
    }

    /** Reads definitions and compiles programs for effects being loaded. */
    static final class Loader {
        private final ResourceManager resourceManager;
        private final Map<ResourceLocation, ScreenEffectDefinition> definitions = new HashMap<>();

        private Loader(ResourceManager resourceManager) {
            this.resourceManager = resourceManager;
        }

        ScreenEffectDefinition definition(ResourceLocation id) throws IOException {
            ScreenEffectDefinition cached = this.definitions.get(id);
            if (cached != null) return cached;
            ScreenEffectDefinition definition = this.read(id);
            this.definitions.put(id, definition);
            return definition;
        }

        // post_effect/<path>.json, or else a vanilla post chain at shaders/post/<path>.json.
        private ScreenEffectDefinition read(ResourceLocation id) throws IOException {
            ResourceLocation location = id.withPath(path -> "post_effect/" + path + ".json");
            Optional<Resource> resource = this.resourceManager.getResource(location);
            if (resource.isPresent()) {
                try (Reader reader = resource.get().openAsReader()) {
                    JsonElement json = JsonParser.parseReader(reader);
                    return ScreenEffectDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                            .getOrThrow(message -> new IOException("Invalid screen effect definition " + location + ": " + message));
                }
            }

            ResourceLocation legacy = id.withPath(path -> "shaders/post/" + path + ".json");
            Optional<Resource> chain = this.resourceManager.getResource(legacy);
            if (chain.isEmpty()) throw new IOException("Missing screen effect definition " + location);
            try (Reader reader = chain.get().openAsReader()) {
                return LegacyPostChain.read(this.resourceManager, legacy, GsonHelper.convertToJsonObject(JsonParser.parseReader(reader), "post chain"));
            } catch (RuntimeException e) {
                throw new IOException("Invalid post chain " + legacy + ": " + e.getMessage(), e);
            }
        }

        EffectProgram compile(ResourceLocation effectId, ScreenEffectDefinition definition, boolean autoBlend) throws IOException {
            return EffectProgram.compile(effectId, definition, autoBlend, this::program);
        }

        private ShaderProgram program(ResourceLocation vertexShader, ResourceLocation fragmentShader) throws IOException {
            String key = vertexShader + "|" + fragmentShader;
            ShaderProgram cached = PROGRAMS.get(key);
            if (cached != null) return cached;
            ShaderProgram program = ShaderProgram.compile(this.resourceManager, vertexShader, fragmentShader, List.of(), ATTRIBUTES);
            PROGRAMS.put(key, program);
            CoolCatCanvas.LOGGER.debug("Compiled screen effect shaders {}", key);
            return program;
        }
    }
}
