package net.ixdarklord.coolcatcanvas.internal.client.sky;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.ixdarklord.coolcatcanvas.api.client.sky.SkyContext;
import net.ixdarklord.coolcatcanvas.api.client.sky.SkyLayerDefinition;
import net.ixdarklord.coolcatcanvas.api.client.sky.SkyLayerStage;
import net.ixdarklord.coolcatcanvas.api.client.sky.Skybox;
import net.ixdarklord.coolcatcanvas.api.client.sky.SkyboxDefinition;
import net.ixdarklord.coolcatcanvas.api.client.sky.VanillaSky;
import net.ixdarklord.coolcatcanvas.internal.client.render.ShaderPacks;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;

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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs skyboxes: GameRenderer's mixin calls {@link #beginFrame} and {@link #reload}; LevelRenderer's calls
 * {@link #beginSky} and {@link #render} from vanilla's sky pass (or {@link #renderWithoutVanillaSky} where there's
 * none), and asks what to hide of vanilla's sky.
 */
public final class SkyboxManager {
    public static final String FOLDER = "skybox";
    private static final Comparator<SkyboxImpl> ORDER = Comparator.comparingInt(SkyboxImpl::priority);
    private static final long START_NANOS = Util.getNanos();
    private static final VanillaSky[] PARTS = VanillaSky.values();

    // Registration may come from mod construction threads; everything else runs on the render thread.
    private static final Map<ResourceLocation, SkyboxImpl> SKYBOXES = new ConcurrentHashMap<>();
    private static volatile boolean orderDirty = true;
    private static SkyboxImpl[] ordered = new SkyboxImpl[0];

    private static final SkyFrame FRAME = new SkyFrame();
    private static final float[] HIDDEN = new float[PARTS.length];
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static boolean resourcesLoaded;
    private static long lastFrameNanos = -1L;
    // Whether any skybox showing this frame has layers to draw.
    private static boolean anyLayers;
    // Whether a shader pack draws the sky this frame: skyboxes then stand aside, drawing and hiding nothing.
    private static boolean shaderPack;
    // Whether the dimension's own sky renderer drew the sky this frame, in place of vanilla's pass.
    private static boolean customSky;

    private SkyboxManager() {}

    public static Skybox register(ResourceLocation id, @Nullable ResourceLocation definitionId, @Nullable SkyboxDefinition definition) {
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

    public static @Nullable Skybox get(ResourceLocation id) {
        return SKYBOXES.get(id);
    }

    public static Collection<Skybox> all() {
        return Collections.unmodifiableCollection(SKYBOXES.values());
    }

    public static boolean unregister(ResourceLocation id) {
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

    public static void beginFrame(float partialTick) {
        long now = Util.getNanos();
        float delta = lastFrameNanos < 0L ? 0.0F : Math.min(1.0F, (now - lastFrameNanos) / 1.0E9F);
        lastFrameNanos = now;
        Arrays.fill(HIDDEN, 0.0F);
        anyLayers = false;
        customSky = false;
        if (!resourcesLoaded || SKYBOXES.isEmpty()) return;
        shaderPack = ShaderPacks.inUse();

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        FRAME.realSeconds = (now - START_NANOS) / 1.0E9;
        if (level != null) {
            FRAME.gameSeconds = (level.getGameTime() + partialTick) / 20.0;
            FRAME.dayTime = Math.floorMod(level.getDayTime(), (long) SkyLayerDefinition.DayFade.DAY_LENGTH);
            FRAME.rain = level.getRainLevel(partialTick);
            FRAME.thunder = level.getThunderLevel(partialTick);
            // Vanilla turns the sun, the moon and the stars together; the moon sits opposite the sun.
            FRAME.sunAngle = level.getTimeOfDay(partialTick) * 360.0F;
            FRAME.moonAngle = FRAME.sunAngle + 180.0F;
            FRAME.starAngle = FRAME.sunAngle;
        } else {
            FRAME.gameSeconds = 0.0;
            FRAME.dayTime = 0.0F;
            FRAME.rain = 0.0F;
            FRAME.thunder = 0.0F;
            FRAME.sunAngle = 0.0F;
            FRAME.moonAngle = 180.0F;
            FRAME.starAngle = 0.0F;
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
            if (!skybox.isVisible() || shaderPack) continue;
            SkyboxProgram program = skybox.program();
            if (program != null && program.hasLayers()) anyLayers = true;
            for (VanillaSky part : skybox.hidden()) HIDDEN[part.ordinal()] = Math.max(HIDDEN[part.ordinal()], skybox.visibility());
        }
    }

    /** At the start of vanilla's sky pass: keeps the camera's rotation and the projection, for the layers. */
    public static void beginSky(Matrix4fc view, Matrix4fc projection) {
        VIEW.set(view);
        PROJECTION.set(projection);
    }

    /**
     * Draws both stages in a dimension whose sky vanilla doesn't draw at all (the Nether), while a skybox with layers
     * shows there.
     */
    public static void renderWithoutVanillaSky(@Nullable ClientLevel level) {
        if (!anyLayers || level == null || level.effects().skyType() != DimensionSpecialEffects.SkyType.NONE) return;
        render(SkyLayerStage.BEHIND_CELESTIALS);
        render(SkyLayerStage.ABOVE_CELESTIALS);
    }

    /**
     * Called by the loader modules when the dimension's own sky renderer (Forge's
     * {@code DimensionSpecialEffects#renderSky}, Fabric API's {@code DimensionRenderingRegistry}) draws the sky this
     * frame instead of vanilla's pass, so the layers go over it once the sky is done ({@link #afterSky}).
     */
    public static void customSkyRendered() {
        customSky = true;
    }

    /**
     * Right after the sky pass: where the dimension's own renderer drew the sky, the layers of both stages go over it,
     * in the sky's fog. What skyboxes hide only applies to vanilla's sky, so nothing of a custom sky is hidden.
     */
    public static void afterSky(Runnable skyFogSetup) {
        if (!customSky) return;
        customSky = false;
        if (!anyLayers) return;
        skyFogSetup.run();
        render(SkyLayerStage.BEHIND_CELESTIALS);
        render(SkyLayerStage.ABOVE_CELESTIALS);
    }

    /** How hidden a part of vanilla's sky is this frame, from 0 (drawn as usual) to 1 (not drawn). */
    public static float hidden(VanillaSky part) {
        return HIDDEN[part.ordinal()];
    }

    /** Vanilla's sky color, faded towards the fog color as far as {@link VanillaSky#SKY} is hidden, into {@code rgb}. */
    public static void skyColor(float[] rgb) {
        float hidden = hidden(VanillaSky.SKY);
        if (hidden <= 0.0F) return;
        float[] fog = RenderSystem.getShaderFogColor();
        for (int i = 0; i < 3; i++) rgb[i] = Mth.lerp(hidden, rgb[i], fog[i]);
    }

    /** Draws the visible skyboxes' layers of a stage, inside vanilla's sky pass. */
    public static void render(SkyLayerStage stage) {
        if (!anyLayers) return;
        RenderSystem.assertOnRenderThread();
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        // Like vanilla's sky: no depth, so the world draws over it all.
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        SkyboxProgram.SkyFog fog = SkyboxProgram.SkyFog.current();
        try {
            for (SkyboxImpl skybox : ordered()) {
                SkyboxProgram program = skybox.isVisible() ? skybox.program() : null;
                if (program != null) program.draw(stage, skybox, FRAME, VIEW, PROJECTION, fog);
            }
        } finally {
            if (depthTest) RenderSystem.enableDepthTest();
            RenderSystem.depthMask(depthMask);
            if (cull) RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            if (blend) RenderSystem.enableBlend();
            else RenderSystem.disableBlend();
        }
    }

    // ---- Resources ----

    /** After vanilla's shaders load: finds resource pack skyboxes, then rebuilds every skybox from the new resources. */
    public static void reload(ResourceManager resourceManager) {
        RenderSystem.assertOnRenderThread();
        resourcesLoaded = true;
        SkyResources.clearPrograms();
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
        Set<ResourceLocation> claimed = new HashSet<>();
        for (SkyboxImpl skybox : SKYBOXES.values()) {
            if (!skybox.fromResources() && skybox.definitionId() != null) claimed.add(skybox.definitionId());
        }

        Set<ResourceLocation> found = new HashSet<>();
        int prefix = FOLDER.length() + 1;
        for (ResourceLocation file : resourceManager.listResources(FOLDER, path -> path.getPath().endsWith(".json")).keySet()) {
            ResourceLocation id = file.withPath(path -> path.substring(prefix, path.length() - ".json".length()));
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
        private final Map<ResourceLocation, SkyboxDefinition> definitions = new HashMap<>();

        private Loader(ResourceManager resourceManager) {
            this.resourceManager = resourceManager;
        }

        SkyboxDefinition definition(ResourceLocation id) throws IOException {
            SkyboxDefinition cached = this.definitions.get(id);
            if (cached != null) return cached;
            ResourceLocation location = id.withPath(path -> FOLDER + "/" + path + ".json");
            Optional<Resource> resource = this.resourceManager.getResource(location);
            if (resource.isEmpty()) throw new IOException("Missing skybox definition " + location);
            try (Reader reader = resource.get().openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                DataResult<SkyboxDefinition> result = SkyboxDefinition.CODEC.parse(JsonOps.INSTANCE, json);
                Optional<DataResult.PartialResult<SkyboxDefinition>> error = result.error();
                if (error.isPresent()) throw new IOException("Invalid skybox definition " + location + ": " + error.get().message());
                SkyboxDefinition definition = result.result().orElseThrow();
                this.definitions.put(id, definition);
                return definition;
            } catch (RuntimeException e) {
                throw new IOException("Invalid skybox definition " + location + ": " + e.getMessage(), e);
            }
        }
    }
}
