package net.ixdarklord.coolcatcanvas.internal.client.sky;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.serialization.JsonOps;
import net.ixdarklord.coolcatcanvas.api.client.sky.SkyContext;
import net.ixdarklord.coolcatcanvas.api.client.sky.SkyLayerDefinition;
import net.ixdarklord.coolcatcanvas.api.client.sky.SkyLayerStage;
import net.ixdarklord.coolcatcanvas.api.client.sky.Skybox;
import net.ixdarklord.coolcatcanvas.api.client.sky.SkyboxDefinition;
import net.ixdarklord.coolcatcanvas.api.client.sky.VanillaSky;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.dimension.DimensionType;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs skyboxes: GameRenderer's mixin calls {@link #beginFrame} and {@link #endFrame}; SkyRenderer's calls
 * {@link #extract} and {@link #render}, and asks what to hide of vanilla's sky; ShaderManager's calls {@link #reload}
 * once shaders are (re)loaded.
 */
public final class SkyboxManager {
    public static final String FOLDER = "skybox";
    private static final Comparator<SkyboxImpl> ORDER = Comparator.comparingInt(SkyboxImpl::priority);
    private static final long START_NANOS = Util.getNanos();
    private static final VanillaSky[] PARTS = VanillaSky.values();

    // Registration may come from mod construction threads; everything else runs on the render thread.
    private static final Map<Identifier, SkyboxImpl> SKYBOXES = new ConcurrentHashMap<>();
    private static volatile boolean orderDirty = true;
    private static SkyboxImpl[] ordered = new SkyboxImpl[0];

    private static final SkyFrame FRAME = new SkyFrame();
    private static final SkyboxProgram.DrawList DRAWS = new SkyboxProgram.DrawList();
    private static final float[] HIDDEN = new float[PARTS.length];
    private static boolean resourcesLoaded;
    private static long lastFrameNanos = -1L;
    // Whether any skybox showing this frame has layers to draw.
    private static boolean anyLayers;
    // Whether this frame's sky pass only runs because a skybox needs it, in a dimension without a sky.
    private static boolean forcedSky;

    private SkyboxManager() {}

    public static Skybox register(Identifier id, @Nullable Identifier definitionId, @Nullable SkyboxDefinition definition) {
        SkyboxImpl skybox = new SkyboxImpl(id, definitionId, definition, false);
        SKYBOXES.compute(id, (key, existing) -> {
            if (existing != null && !existing.fromResources()) throw new IllegalArgumentException("Skybox " + id + " is already registered");
            return skybox;
        });
        // Code takes over a definition a resource pack would otherwise show on its own.
        if (definitionId != null && !definitionId.equals(id)) SKYBOXES.computeIfPresent(definitionId, (key, existing) -> existing.fromResources() ? null : existing);
        markOrderDirty();
        return skybox;
    }

    public static @Nullable Skybox get(Identifier id) {
        return SKYBOXES.get(id);
    }

    public static Collection<Skybox> all() {
        return Collections.unmodifiableCollection(SKYBOXES.values());
    }

    public static boolean unregister(Identifier id) {
        RenderSystem.assertOnRenderThread();
        SkyboxImpl skybox = SKYBOXES.remove(id);
        if (skybox == null) return false;
        skybox.unload();
        markOrderDirty();
        return true;
    }

    static void markOrderDirty() {
        orderDirty = true;
    }

    private static SkyboxImpl[] ordered() {
        if (orderDirty) {
            orderDirty = false;
            SkyboxImpl[] sorted = SKYBOXES.values().toArray(new SkyboxImpl[0]);
            Arrays.sort(sorted, ORDER);
            ordered = sorted;
        }
        return ordered;
    }

    // ---- Frame ----

    public static void beginFrame(DeltaTracker deltaTracker) {
        long now = Util.getNanos();
        float delta = lastFrameNanos < 0L ? 0.0F : Math.min(1.0F, (now - lastFrameNanos) / 1.0E9F);
        lastFrameNanos = now;
        Arrays.fill(HIDDEN, 0.0F);
        anyLayers = false;
        if (!resourcesLoaded || SKYBOXES.isEmpty()) return;

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        FRAME.realSeconds = (now - START_NANOS) / 1.0E9;
        if (level != null) {
            FRAME.gameSeconds = (level.getGameTime() + partialTick) / 20.0;
            FRAME.dayTime = Math.floorMod(level.getDefaultClockTime(), (long) SkyLayerDefinition.DayFade.DAY_LENGTH);
            FRAME.rain = level.getRainLevel(partialTick);
            FRAME.thunder = level.getThunderLevel(partialTick);
        } else {
            FRAME.gameSeconds = 0.0;
            FRAME.dayTime = 0.0F;
            FRAME.rain = 0.0F;
            FRAME.thunder = 0.0F;
        }

        Frame frame = new Frame(minecraft, partialTick, (float) (FRAME.realSeconds % 3600.0), delta, FRAME.dayTime, FRAME.rain, FRAME.thunder);
        Loader loader = null;
        for (SkyboxImpl skybox : ordered()) {
            if (skybox.needsLoad()) skybox.load(loader != null ? loader : (loader = new Loader(minecraft.getResourceManager())));
            if (skybox.error() != null) continue;
            try {
                skybox.update(frame);
            } catch (RuntimeException e) {
                skybox.fail("A callback threw", e);
                skybox.disableInstantly();
                continue;
            }
            if (!skybox.isVisible()) continue;
            SkyboxProgram program = skybox.program();
            if (program != null && program.hasLayers()) anyLayers = true;
            for (VanillaSky part : skybox.hidden()) HIDDEN[part.ordinal()] = Math.max(HIDDEN[part.ordinal()], skybox.visibility());
        }
    }

    public static void endFrame() {
        SkyResources.endFrame();
    }

    /**
     * After vanilla extracts its sky: keeps the angles layers can follow, and gives dimensions without a sky one while
     * a skybox with layers shows, its vanilla parts all hidden.
     */
    public static void extract(SkyRenderState state) {
        forcedSky = false;
        if (state.skybox == DimensionType.Skybox.OVERWORLD) {
            FRAME.sunAngle = state.sunAngle * Mth.RAD_TO_DEG;
            FRAME.moonAngle = state.moonAngle * Mth.RAD_TO_DEG;
            FRAME.starAngle = state.starAngle * Mth.RAD_TO_DEG;
        } else if (state.skybox == DimensionType.Skybox.NONE && anyLayers) {
            forcedSky = true;
            state.skybox = DimensionType.Skybox.OVERWORLD;
            state.sunriseAndSunsetColor = 0;
            state.rainBrightness = 0.0F;
            state.starBrightness = 0.0F;
            state.shouldRenderDarkDisc = false;
            FRAME.sunAngle = 0.0F;
            FRAME.moonAngle = 0.0F;
            FRAME.starAngle = 0.0F;
        }
    }

    /** How hidden a part of vanilla's sky is this frame, from 0 (drawn as usual) to 1 (not drawn). */
    public static float hidden(VanillaSky part) {
        return forcedSky ? 1.0F : HIDDEN[part.ordinal()];
    }

    /** Vanilla's sky color, faded towards the fog color as far as {@link VanillaSky#SKY} is hidden. */
    public static int skyColor(int skyColor) {
        float hidden = hidden(VanillaSky.SKY);
        if (hidden <= 0.0F) return skyColor;
        Vector4f fog = Minecraft.getInstance().gameRenderer.getGameRenderState().levelRenderState.cameraRenderState.fogData.color;
        return ARGB.srgbLerp(hidden, skyColor, ARGB.colorFromFloat(ARGB.alphaFloat(skyColor), fog.x(), fog.y(), fog.z()));
    }

    /** Draws the visible skyboxes' layers of a stage, inside vanilla's sky pass. */
    public static void render(SkyLayerStage stage) {
        if (!anyLayers) return;
        Matrix4fc view = RenderSystem.getModelViewMatrix();
        try {
            for (SkyboxImpl skybox : ordered()) {
                SkyboxProgram program = skybox.isVisible() ? skybox.program() : null;
                if (program == null) continue;
                try {
                    program.collect(stage, skybox, FRAME, view, DRAWS);
                } catch (RuntimeException e) {
                    skybox.fail("Failed to prepare its layers", e);
                }
            }
            if (!DRAWS.isEmpty()) DRAWS.drawAll(SkyboxManager::openPass);
        } finally {
            DRAWS.clear();
        }
    }

    private static RenderPass openPass() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        return RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "CoolCatCore skyboxes", main.getColorTextureView(), OptionalInt.empty(), main.getDepthTextureView(), OptionalDouble.empty());
    }

    // ---- Resources ----

    /** After shaders load: finds resource pack skyboxes, then rebuilds every skybox from the new resources. */
    public static void reload(ResourceManager resourceManager) {
        RenderSystem.assertOnRenderThread();
        resourcesLoaded = true;
        SkyResources.clearPipelines();
        Loader loader = new Loader(resourceManager);
        discover(resourceManager, loader);
        for (SkyboxImpl skybox : SKYBOXES.values()) skybox.load(loader);
    }

    public static void close() {
        for (SkyboxImpl skybox : SKYBOXES.values()) skybox.unload();
        SkyResources.close();
    }

    // Every skybox file listing dimensions shows there by itself, unless code already uses it.
    private static void discover(ResourceManager resourceManager, Loader loader) {
        Set<Identifier> claimed = new HashSet<>();
        for (SkyboxImpl skybox : SKYBOXES.values()) {
            if (!skybox.fromResources() && skybox.definitionId() != null) claimed.add(skybox.definitionId());
        }

        Set<Identifier> found = new HashSet<>();
        int prefix = FOLDER.length() + 1;
        for (Identifier file : resourceManager.listResources(FOLDER, path -> path.getPath().endsWith(".json")).keySet()) {
            Identifier id = file.withPath(path -> path.substring(prefix, path.length() - ".json".length()));
            if (claimed.contains(id)) continue;
            SkyboxDefinition definition;
            try {
                definition = loader.definition(id);
            } catch (IOException | RuntimeException e) {
                CoolCatCanvas.LOGGER.error("Skipping skybox {}: {}", id, e.getMessage());
                continue;
            }
            if (definition.dimensions().isEmpty()) continue;
            found.add(id);
            SKYBOXES.computeIfAbsent(id, key -> new SkyboxImpl(key, key, null, true));
        }

        SKYBOXES.values().removeIf(skybox -> {
            if (!skybox.fromResources() || found.contains(skybox.id())) return false;
            skybox.unload();
            return true;
        });
        markOrderDirty();
    }

    /** One frame's shared values, turned into each skybox's context. */
    record Frame(Minecraft minecraft, float partialTick, float time, float deltaTime, float dayTime, float rain, float thunder) {
        SkyContext context(SkyboxImpl skybox, float age, float visibility) {
            return new SkyContext(skybox, this.minecraft.player, this.minecraft.level, this.partialTick, this.time, this.deltaTime, age, visibility, this.dayTime, this.rain, this.thunder);
        }
    }

    /** Reads skybox definitions, each once per reload. */
    static final class Loader {
        private final ResourceManager resourceManager;
        private final Map<Identifier, SkyboxDefinition> definitions = new HashMap<>();

        private Loader(ResourceManager resourceManager) {
            this.resourceManager = resourceManager;
        }

        SkyboxDefinition definition(Identifier id) throws IOException {
            SkyboxDefinition cached = this.definitions.get(id);
            if (cached != null) return cached;
            Identifier location = id.withPath(path -> FOLDER + "/" + path + ".json");
            Optional<Resource> resource = this.resourceManager.getResource(location);
            if (resource.isEmpty()) throw new IOException("Missing skybox definition " + location);
            try (Reader reader = resource.get().openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                SkyboxDefinition definition = SkyboxDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                        .getOrThrow(message -> new IOException("Invalid skybox definition " + location + ": " + message));
                this.definitions.put(id, definition);
                return definition;
            }
        }
    }
}
