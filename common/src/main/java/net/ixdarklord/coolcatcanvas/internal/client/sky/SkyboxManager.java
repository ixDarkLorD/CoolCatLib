package net.ixdarklord.coolcatcanvas.internal.client.sky;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.serialization.JsonOps;
import net.ixdarklord.coolcatcanvas.api.client.sky.SkyContext;
import net.ixdarklord.coolcatcanvas.api.client.sky.SkyLayerDefinition;
import net.ixdarklord.coolcatcanvas.api.client.sky.SkyLayerStage;
import net.ixdarklord.coolcatcanvas.api.client.sky.Skybox;
import net.ixdarklord.coolcatcanvas.api.client.sky.SkyboxDefinition;
import net.ixdarklord.coolcatcanvas.api.client.sky.VanillaSky;
import net.ixdarklord.coolcatcanvas.internal.client.effect.ShaderProgram;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jetbrains.annotations.Nullable;

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
 * Runs skyboxes: GameRenderer's mixin calls {@link #beginFrame}, {@link #endFrame} and {@link #reload};
 * LevelRenderer's calls {@link #beginSky} as the sky pass starts, {@link #render} per stage, and asks what to hide of
 * vanilla's sky.
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
    private static boolean resourcesLoaded;
    private static long lastFrameNanos = -1L;
    // Whether any skybox showing this frame has layers to draw.
    private static boolean anyLayers;
    // Whether this frame's sky pass only runs because a skybox needs it, in a dimension without a sky.
    private static boolean forcedSky;
    // The sun and moon's alpha before hiding, faded by rain.
    private static float celestialAlpha = 1.0F;

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
            FRAME.dayTime = Math.floorMod(level.getDayTime(), (long) SkyLayerDefinition.DayFade.DAY_LENGTH);
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

    public static void endFrame() {}

    /**
     * As vanilla's sky pass starts: keeps the camera and the angles layers can follow. In a dimension without a sky,
     * while a skybox with layers shows, draws them as the whole sky; true if it did, so vanilla's pass is skipped.
     */
    public static boolean beginSky(Matrix4f view, Matrix4f projection, float partialTick, Camera camera, Runnable skyFogSetup) {
        forcedSky = false;
        FRAME.view.set(view);
        FRAME.projection.set(projection);
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return false;
        DimensionSpecialEffects.SkyType type = level.effects().skyType();
        if (type == DimensionSpecialEffects.SkyType.NORMAL) {
            // Vanilla turns the sun, moon and stars together, by the time of day; the moon is opposite the sun (26.1's
            // moon angle is the sun's plus 180, so layers following it are overhead at midnight).
            float angle = level.getTimeOfDay(partialTick) * 360.0F;
            FRAME.sunAngle = angle;
            FRAME.moonAngle = (angle + 180.0F) % 360.0F;
            FRAME.starAngle = angle;
            return false;
        }
        if (type != DimensionSpecialEffects.SkyType.NONE || !anyLayers || blocksSky(camera)) return false;

        forcedSky = true;
        FRAME.sunAngle = 0.0F;
        FRAME.moonAngle = 0.0F;
        FRAME.starAngle = 0.0F;
        skyFogSetup.run();
        RenderSystem.depthMask(false);
        render(SkyLayerStage.BEHIND_CELESTIALS);
        render(SkyLayerStage.ABOVE_CELESTIALS);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        return true;
    }

    // Where vanilla draws no sky at all.
    private static boolean blocksSky(Camera camera) {
        FogType fog = camera.getFluidInCamera();
        if (fog == FogType.POWDER_SNOW || fog == FogType.LAVA) return true;
        return camera.getEntity() instanceof LivingEntity living && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS));
    }

    /** How hidden a part of vanilla's sky is this frame, from 0 (drawn as usual) to 1 (not drawn). */
    public static float hidden(VanillaSky part) {
        return forcedSky ? 1.0F : HIDDEN[part.ordinal()];
    }

    /** Vanilla's sky color, faded towards the fog color as far as {@link VanillaSky#SKY} is hidden. */
    public static Vec3 skyColor(Vec3 skyColor) {
        float hidden = hidden(VanillaSky.SKY);
        if (hidden <= 0.0F) return skyColor;
        float[] fog = RenderSystem.getShaderFogColor();
        return new Vec3(Mth.lerp(hidden, skyColor.x, fog[0]), Mth.lerp(hidden, skyColor.y, fog[1]), Mth.lerp(hidden, skyColor.z, fog[2]));
    }

    /** Vanilla's sunrise glow, faded out as far as {@link VanillaSky#SUNRISE} is hidden; null skips it. */
    public static float @Nullable [] sunriseColor(float @Nullable [] color) {
        float hidden = hidden(VanillaSky.SUNRISE);
        if (color == null || hidden <= 0.0F) return color;
        if (hidden >= 1.0F) return null;
        float[] faded = color.clone();
        faded[3] *= 1.0F - hidden;
        return faded;
    }

    /** Fades the sun or the moon, about to be drawn with the shader color. */
    public static void fadeCelestial(VanillaSky part) {
        float[] color = RenderSystem.getShaderColor();
        float red = color[0];
        float green = color[1];
        float blue = color[2];
        if (part == VanillaSky.SUN) celestialAlpha = color[3];
        RenderSystem.setShaderColor(red, green, blue, celestialAlpha * (1.0F - hidden(part)));
    }

    /** Fades the stars, about to be drawn with the shader color. */
    public static void fadeStars() {
        float hidden = hidden(VanillaSky.STARS);
        if (hidden <= 0.0F) return;
        float[] color = RenderSystem.getShaderColor();
        float shown = 1.0F - hidden;
        RenderSystem.setShaderColor(color[0] * shown, color[1] * shown, color[2] * shown, color[3] * shown);
    }

    /** As the End sky starts; true if it's fully hidden, so skipped (its layers drawn instead). */
    public static boolean beginEndSky() {
        float hidden = hidden(VanillaSky.END_SKY);
        if (hidden >= 1.0F) {
            renderEndLayers();
            return true;
        }
        if (hidden > 0.0F) RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F - hidden);
        return false;
    }

    /** After the End sky: both stages of layers. */
    public static void endEndSky() {
        if (hidden(VanillaSky.END_SKY) > 0.0F) RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        renderEndLayers();
    }

    private static void renderEndLayers() {
        if (!anyLayers) return;
        RenderSystem.depthMask(false);
        render(SkyLayerStage.BEHIND_CELESTIALS);
        render(SkyLayerStage.ABOVE_CELESTIALS);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    /** Draws the visible skyboxes' layers of a stage, inside vanilla's sky pass (depth writes already off). */
    public static void render(SkyLayerStage stage) {
        if (!anyLayers) return;
        RenderSystem.disableCull();
        try {
            for (SkyboxImpl skybox : ordered()) {
                SkyboxProgram program = skybox.isVisible() ? skybox.program() : null;
                if (program != null) program.render(stage, skybox, FRAME);
            }
        } finally {
            VertexBuffer.unbind();
            ShaderProgram.release();
            RenderSystem.enableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    // ---- Resources ----

    /** After shaders load: finds resource pack skyboxes, then rebuilds every skybox from the new resources. */
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
                SkyboxDefinition definition = SkyboxDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                        .getOrThrow(message -> new IOException("Invalid skybox definition " + location + ": " + message));
                this.definitions.put(id, definition);
                return definition;
            }
        }
    }
}
