package net.ixdarklord.coolcatcanvas.internal.client.vfx;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.ixdarklord.coolcatcanvas.api.client.vfx.Curve;
import net.ixdarklord.coolcatcanvas.api.client.vfx.ParticleSpec;
import net.ixdarklord.coolcatcanvas.api.client.vfx.VfxBlend;
import net.ixdarklord.coolcatcanvas.api.client.vfx.VfxDraw;
import net.ixdarklord.coolcatcanvas.api.client.vfx.VfxEffect;
import net.ixdarklord.coolcatcanvas.api.client.vfx.VfxFrame;
import net.ixdarklord.coolcatcanvas.api.client.vfx.VfxMaterial;
import net.ixdarklord.coolcatcanvas.api.client.vfx.VfxRenderer;
import net.ixdarklord.coolcatcanvas.api.client.vfx.WorldParticles;
import net.ixdarklord.coolcatcanvas.api.vfx.VfxParams;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientPlayerEvents;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientTickEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

// World effects: owns the particles and short-lived shapes, gathers everything drawn in a frame into one batch per
// material, and draws the batches in one render pass on the main target, right after the level and before the hand.
// The pass has no depth attachment; the shader tests against the scene's depth itself (see vfx/quad.fsh).
public final class VfxManager {
    private static final float NEAR_PLANE = 0.05F;
    private static final Identifier SHADER = CoolCatCanvas.rl("vfx/quad");

    private static final Map<Identifier, VfxRenderer> RENDERERS = new LinkedHashMap<>();
    private static final Map<Identifier, VfxEffect> EFFECTS = new HashMap<>();
    private static final List<Shape> SHAPES = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();

    // ---- Particles, as parallel arrays ----
    private static final int MAX = WorldParticles.MAX;
    private static final ParticleSpec[] spec = new ParticleSpec[MAX];
    private static final double[] px = new double[MAX], py = new double[MAX], pz = new double[MAX];
    private static final double[] ox = new double[MAX], oy = new double[MAX], oz = new double[MAX];
    private static final float[] vx = new float[MAX], vy = new float[MAX], vz = new float[MAX];
    private static final float[] roll = new float[MAX], oldRoll = new float[MAX], spinSign = new float[MAX];
    private static final int[] age = new int[MAX], lifetime = new int[MAX];
    private static int count;
    // A spec's two colours as hue, saturation and value, worked out once.
    private static final Map<ParticleSpec, float[]> HSV = new IdentityHashMap<>();

    // ---- Drawing ----
    private static final VfxCamera CAMERA = new VfxCamera();
    private static final Map<VfxMaterial, VfxBatch> BATCHES = new HashMap<>();
    private static final List<VfxBatch> DRAWN = new ArrayList<>();
    private static final Map<VfxMaterial, RenderPipeline> PIPELINES = new HashMap<>();
    private static final FrameImpl FRAME = new FrameImpl();
    private static @Nullable ByteBufferBuilder bytes;
    private static @Nullable DynamicUniformStorage<VfxUniform> uniforms;
    private static @Nullable String error;

    private VfxManager() {}

    public static void init() {
        ClientTickEvents.END.register(VfxManager::tick);
        ClientPlayerEvents.LEAVE.register(player -> {
            clearParticles();
            SHAPES.clear();
        });
    }

    // ---- Registries ----

    public static void register(Identifier id, VfxRenderer renderer) {
        RENDERERS.put(id, renderer);
    }

    public static void unregister(Identifier id) {
        RENDERERS.remove(id);
    }

    public static void registerEffect(Identifier id, VfxEffect effect) {
        EFFECTS.put(id, effect);
    }

    public static void unregisterEffect(Identifier id) {
        EFFECTS.remove(id);
    }

    /** Plays a named effect in the world the client is in; unknown ids are skipped (the client may lack the mod). */
    public static boolean playEffect(Identifier id, VfxParams params) {
        VfxEffect effect = EFFECTS.get(id);
        ClientLevel level = Minecraft.getInstance().level;
        if (effect == null || level == null) {
            if (effect == null) CoolCatCanvas.LOGGER.debug("Asked to play unknown world effect {}", id);
            return false;
        }
        try {
            effect.play(level, params);
        } catch (RuntimeException e) {
            CoolCatCanvas.LOGGER.error("World effect {} failed", id, e);
        }
        return true;
    }

    // ---- Particles ----

    public static void spawn(ParticleSpec particle, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
        if (count >= MAX || Minecraft.getInstance().level == null) return;
        int i = count++;
        spec[i] = particle;
        px[i] = ox[i] = x;
        py[i] = oy[i] = y;
        pz[i] = oz[i] = z;
        vx[i] = (float) velocityX;
        vy[i] = (float) velocityY;
        vz[i] = (float) velocityZ;
        roll[i] = oldRoll[i] = particle.randomRoll() ? RANDOM.nextFloat() * Mth.TWO_PI : 0.0F;
        spinSign[i] = RANDOM.nextBoolean() ? 1.0F : -1.0F;
        age[i] = 0;
        lifetime[i] = particle.minLifetime() + RANDOM.nextInt(particle.maxLifetime() - particle.minLifetime() + 1);
    }

    public static int particleCount() {
        return count;
    }

    public static void clearParticles() {
        java.util.Arrays.fill(spec, 0, count, null);
        count = 0;
    }

    private static void tick(Minecraft minecraft) {
        if (minecraft.level == null) {
            if (count > 0) clearParticles();
            SHAPES.clear();
            return;
        }
        if (minecraft.isPaused()) return;
        for (int i = 0; i < count; i++) {
            if (++age[i] >= lifetime[i]) {
                remove(i--);
                continue;
            }
            ParticleSpec particle = spec[i];
            ox[i] = px[i];
            oy[i] = py[i];
            oz[i] = pz[i];
            vy[i] -= particle.gravity();
            float drag = particle.drag();
            vx[i] *= drag;
            vy[i] *= drag;
            vz[i] *= drag;
            px[i] += vx[i];
            py[i] += vy[i];
            pz[i] += vz[i];
            oldRoll[i] = roll[i];
            roll[i] += particle.spin().at(age[i] / (float) lifetime[i]) * spinSign[i];
        }
        SHAPES.removeIf(shape -> ++shape.age >= shape.ticks);
    }

    // Swap-remove: the last particle takes the dead one's place.
    private static void remove(int i) {
        int last = --count;
        spec[i] = spec[last];
        px[i] = px[last];
        py[i] = py[last];
        pz[i] = pz[last];
        ox[i] = ox[last];
        oy[i] = oy[last];
        oz[i] = oz[last];
        vx[i] = vx[last];
        vy[i] = vy[last];
        vz[i] = vz[last];
        roll[i] = roll[last];
        oldRoll[i] = oldRoll[last];
        spinSign[i] = spinSign[last];
        age[i] = age[last];
        lifetime[i] = lifetime[last];
        spec[last] = null;
    }

    // ---- Short-lived shapes ----

    public static void addBeam(VfxMaterial material, Vec3 from, Vec3 to, float width, int argb, int ticks) {
        if (Minecraft.getInstance().level != null) SHAPES.add(new Shape(material, from, to, Curve.constant(width), argb, Math.max(1, ticks), Kind.BEAM));
    }

    public static void addFlash(VfxMaterial material, Vec3 position, Curve size, int argb, int ticks, boolean onGround) {
        if (Minecraft.getInstance().level != null) SHAPES.add(new Shape(material, position, position, size, argb, Math.max(1, ticks), onGround ? Kind.GROUND : Kind.FLASH));
    }

    private enum Kind {
        BEAM, FLASH, GROUND
    }

    private static final class Shape {
        final VfxMaterial material;
        final Vec3 from;
        final Vec3 to;
        final Curve size;
        final int argb;
        final int ticks;
        final Kind kind;
        int age;

        Shape(VfxMaterial material, Vec3 from, Vec3 to, Curve size, int argb, int ticks, Kind kind) {
            this.material = material;
            this.from = from;
            this.to = to;
            this.size = size;
            this.argb = argb;
            this.ticks = ticks;
            this.kind = kind;
        }
    }

    // ---- Frame ----

    /** Draws every world effect. Called with the level drawn, its projection still set and its depth still there. */
    public static void render(CameraRenderState camera, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (error != null || level == null || (count == 0 && SHAPES.isEmpty() && RENDERERS.isEmpty())) return;
        try {
            // (Paused, the game holds the part of a tick it had reached, so everything stays exactly where it was.)
            float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
            CAMERA.set(camera);
            FRAME.level = level;
            FRAME.partialTick = partialTick;
            // Its clock stops while the game is paused: renderers that animate by it, and shaders by their Time, stand still.
            FRAME.time = (float) (net.ixdarklord.coolcatcanvas.internal.client.GameClock.seconds() % 3600.0);
            gatherParticles(partialTick);
            gatherShapes(partialTick);
            for (Map.Entry<Identifier, VfxRenderer> renderer : RENDERERS.entrySet()) {
                try {
                    renderer.getValue().render(FRAME);
                } catch (RuntimeException e) {
                    CoolCatCanvas.LOGGER.error("World effect renderer {} failed and was removed", renderer.getKey(), e);
                    RENDERERS.remove(renderer.getKey());
                    break;
                }
            }
            draw(minecraft);
        } catch (RuntimeException e) {
            fail("Failed to draw world effects", e);
        } finally {
            for (VfxBatch batch : BATCHES.values()) batch.clear();
            DRAWN.clear();
            FRAME.level = null;
        }
    }

    private static VfxBatch batch(VfxMaterial material) {
        return BATCHES.computeIfAbsent(material, key -> new VfxBatch(key, CAMERA));
    }

    private static void gatherParticles(float partialTick) {
        VfxBatch batch = null;
        ParticleSpec batchSpec = null;
        for (int i = 0; i < count; i++) {
            ParticleSpec particle = spec[i];
            float progress = Math.min(1.0F, (age[i] + partialTick) / lifetime[i]);
            float size = particle.size().at(progress);
            float opacity = Mth.clamp(particle.opacity().at(progress), 0.0F, 1.0F);
            if (size <= 0.0F || opacity <= 0.003F) continue;
            if (particle != batchSpec) {
                batchSpec = particle;
                batch = batch(particle.material());
            }
            int color = ARGB.color(Math.round(opacity * 255.0F), color(particle, progress));
            float x = CAMERA.relX(Mth.lerp(partialTick, ox[i], px[i]));
            float y = CAMERA.relY(Mth.lerp(partialTick, oy[i], py[i]));
            float z = CAMERA.relZ(Mth.lerp(partialTick, oz[i], pz[i]));
            if (particle.shape() == ParticleSpec.Shape.SPARK) {
                float stretch = particle.stretch();
                float tx = vx[i] * stretch, ty = vy[i] * stretch, tz = vz[i] * stretch;
                if (tx * tx + ty * ty + tz * tz > 1.0E-6F) {
                    batch.beamRelative(x - tx, y - ty, z - tz, x, y, z, size * 2.0F, color, color);
                    continue;
                }
            }
            batch.billboardRelative(x, y, z, size, Mth.lerp(partialTick, oldRoll[i], roll[i]), color);
        }
    }

    /** A spec's colour at a point in its life: around the colour wheel between its two ends. */
    private static int color(ParticleSpec particle, float progress) {
        if (particle.startColor() == particle.endColor()) return particle.startColor();
        float[] hsv = HSV.computeIfAbsent(particle, key -> {
            float[] values = new float[6];
            toHsv(key.startColor(), values, 0);
            toHsv(key.endColor(), values, 3);
            // The short way around the wheel; a grey end takes the other end's hue.
            if (values[1] < 1.0E-3F) values[0] = values[3];
            if (values[4] < 1.0E-3F) values[3] = values[0];
            if (values[3] - values[0] > 0.5F) values[0] += 1.0F;
            else if (values[0] - values[3] > 0.5F) values[3] += 1.0F;
            return values;
        });
        float t = particle.colorEasing().applyClamped(progress);
        float hue = Mth.lerp(t, hsv[0], hsv[3]);
        return Mth.hsvToRgb(hue - Mth.floor(hue), Mth.lerp(t, hsv[1], hsv[4]), Mth.lerp(t, hsv[2], hsv[5])) & 0xFFFFFF;
    }

    private static void toHsv(int rgb, float[] out, int at) {
        float r = (rgb >> 16 & 0xFF) / 255.0F, g = (rgb >> 8 & 0xFF) / 255.0F, b = (rgb & 0xFF) / 255.0F;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float hue = 0.0F;
        if (delta > 1.0E-6F) {
            if (max == r) hue = ((g - b) / delta) % 6.0F;
            else if (max == g) hue = (b - r) / delta + 2.0F;
            else hue = (r - g) / delta + 4.0F;
            hue /= 6.0F;
            if (hue < 0.0F) hue += 1.0F;
        }
        out[at] = hue;
        out[at + 1] = max <= 0.0F ? 0.0F : delta / max;
        out[at + 2] = max;
    }

    private static void gatherShapes(float partialTick) {
        for (Shape shape : SHAPES) {
            float progress = Math.min(1.0F, (shape.age + partialTick) / shape.ticks);
            float fade = 1.0F - progress * progress;
            int color = ARGB.color(Math.round(ARGB.alpha(shape.argb) * fade), shape.argb);
            VfxBatch batch = batch(shape.material);
            switch (shape.kind) {
                case BEAM -> batch.beam(shape.from, shape.to, shape.size.start() * (1.0F - progress * 0.6F), color);
                case FLASH -> batch.billboard(shape.from.x, shape.from.y, shape.from.z, shape.size.at(progress), 0.0F, color);
                case GROUND -> batch.flat(shape.from.x, shape.from.y, shape.from.z, shape.size.at(progress), 0.0F, color);
            }
        }
    }

    private static void draw(Minecraft minecraft) {
        // What blends over the scene goes first, what adds light to it last.
        int vertices = 0;
        for (int phase = 0; phase < 2; phase++) {
            for (VfxBatch batch : BATCHES.values()) {
                if (batch.quads() == 0 || (batch.material.blend() == VfxBlend.ADDITIVE) != (phase == 1)) continue;
                batch.firstVertex = vertices;
                vertices += batch.quads() * VfxBatch.VERTICES_PER_QUAD;
                DRAWN.add(batch);
            }
        }
        if (DRAWN.isEmpty()) return;

        RenderTarget main = minecraft.getMainRenderTarget();
        GpuTextureView depth = main.getDepthTextureView();
        if (depth == null) return;
        if (bytes == null) bytes = new ByteBufferBuilder(DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize() * 4096);
        if (uniforms == null) uniforms = new DynamicUniformStorage<>("CoolCatLib: Canvas world effects UBO", VfxUniform.SIZE, 8);

        // Everything that touches buffers or textures happens before the pass opens.
        BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
        TextureManager textureManager = minecraft.getTextureManager();
        VfxUniform[] blocks = new VfxUniform[DRAWN.size()];
        GpuTextureView[] textures = new GpuTextureView[DRAWN.size()];
        RenderPipeline[] pipelines = new RenderPipeline[DRAWN.size()];
        for (int i = 0; i < blocks.length; i++) {
            VfxBatch batch = DRAWN.get(i);
            batch.emit(builder);
            VfxUniform uniform = batch.uniform;
            uniform.view.set(CAMERA.view);
            uniform.near = NEAR_PLANE;
            uniform.far = CAMERA.far;
            uniform.softness = batch.material.softness();
            uniform.luminanceAlpha = batch.material.blend().luminanceAlpha() ? 1.0F : 0.0F;
            uniform.time = FRAME.time;
            blocks[i] = uniform;
            textures[i] = textureManager.getTexture(batch.material.texture()).getTextureView();
            pipelines[i] = pipeline(batch.material);
        }
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        try (MeshData mesh = builder.buildOrThrow()) {
            GpuBuffer vertexBuffer = DefaultVertexFormat.POSITION_TEX_COLOR.uploadImmediateVertexBuffer(mesh.vertexBuffer());
            GpuBufferSlice[] slices = uniforms.writeUniforms(blocks);
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                    .createRenderPass(() -> "CoolCatLib: Canvas world effects", main.getColorTextureView(), OptionalInt.empty(), null, OptionalDouble.empty())) {
                for (int i = 0; i < blocks.length; i++) {
                    VfxBatch batch = DRAWN.get(i);
                    pass.setPipeline(pipelines[i]);
                    RenderSystem.bindDefaultUniforms(pass);
                    pass.setUniform(VfxUniform.BLOCK, slices[i]);
                    pass.bindTexture("Sampler0", textures[i], linear);
                    pass.bindTexture("DepthSampler", depth, nearest);
                    pass.setVertexBuffer(0, vertexBuffer);
                    pass.draw(batch.firstVertex, batch.quads() * VfxBatch.VERTICES_PER_QUAD);
                }
            }
        }
    }

    // Keyed by a material with no texture and no softness: what a pipeline depends on is the blend and the shader.
    private static RenderPipeline pipeline(VfxMaterial material) {
        VfxBlend blend = material.blend();
        Identifier fragment = material.shader() == null ? SHADER : material.shader();
        VfxMaterial key = new VfxMaterial(SHADER, blend, 0.0F, fragment);
        RenderPipeline cached = PIPELINES.get(key);
        if (cached != null) return cached;
        RenderPipeline pipeline = RenderPipeline.builder()
                .withLocation(CoolCatCanvas.rl("pipeline/vfx_" + blend.name().toLowerCase(java.util.Locale.ROOT) + "_" + fragment.getNamespace() + "_" + fragment.getPath().replace('/', '_')))
                .withVertexShader(SHADER)
                .withFragmentShader(fragment)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withUniform(VfxUniform.BLOCK, UniformType.UNIFORM_BUFFER)
                .withSampler("Sampler0")
                .withSampler("DepthSampler")
                .withColorTargetState(new ColorTargetState(blend.function()))
                .withCull(false)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.TRIANGLES)
                .build();
        CompiledRenderPipeline compiled = RenderSystem.getDevice().precompilePipeline(pipeline);
        if (!compiled.isValid()) throw new IllegalStateException("The world effects shader " + fragment + " failed to compile or link (see the log above)");
        PIPELINES.put(key, pipeline);
        return pipeline;
    }

    private static void fail(String message, RuntimeException e) {
        error = message + ": " + e.getMessage();
        CoolCatCanvas.LOGGER.error("{}; world effects are off until resources reload", message, e);
    }

    // ---- Lifecycle ----

    public static void endFrame() {
        if (uniforms != null) uniforms.endFrame();
    }

    /** After shaders reload: pipelines are rebuilt on next use, and a failure gets another chance. */
    public static void reload() {
        PIPELINES.clear();
        error = null;
    }

    public static void close() {
        PIPELINES.clear();
        if (bytes != null) {
            bytes.close();
            bytes = null;
        }
        if (uniforms != null) {
            uniforms.close();
            uniforms = null;
        }
    }

    private static final class FrameImpl implements VfxFrame {
        @Nullable ClientLevel level;
        float partialTick;
        float time;

        @Override
        public ClientLevel level() {
            return this.level;
        }

        @Override
        public float partialTick() {
            return this.partialTick;
        }

        @Override
        public float time() {
            return this.time;
        }

        @Override
        public Vec3 cameraPosition() {
            return new Vec3(CAMERA.x, CAMERA.y, CAMERA.z);
        }

        @Override
        public VfxDraw draw(VfxMaterial material) {
            return batch(material);
        }
    }
}
