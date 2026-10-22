package net.ixdarklord.coolcatcanvas.internal.client.vfx;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
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
import net.ixdarklord.coolcatcanvas.internal.client.GameClock;
import net.ixdarklord.coolcatcanvas.internal.client.effect.ShaderProgram;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientPlayerEvents;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientTickEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// World effects: owns the particles and short-lived shapes, gathers everything drawn in a frame into one batch per
// material, and draws the batches on the main target, right after the level and before the hand. They are drawn with
// no depth test; the shader tests against a copy of the scene's depth itself (see vfx/quad.fsh).
public final class VfxManager {
    private static final float NEAR_PLANE = 0.05F;
    private static final ResourceLocation SHADER = CoolCatCanvas.rl("vfx/quad");

    private static final Map<ResourceLocation, VfxRenderer> RENDERERS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, VfxEffect> EFFECTS = new HashMap<>();
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
    private static final Map<ResourceLocation, ShaderProgram> PROGRAMS = new HashMap<>();
    private static final FrameImpl FRAME = new FrameImpl();
    private static @Nullable TextureTarget worldDepth;
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

    public static void register(ResourceLocation id, VfxRenderer renderer) {
        RENDERERS.put(id, renderer);
    }

    public static void unregister(ResourceLocation id) {
        RENDERERS.remove(id);
    }

    public static void registerEffect(ResourceLocation id, VfxEffect effect) {
        EFFECTS.put(id, effect);
    }

    public static void unregisterEffect(ResourceLocation id) {
        EFFECTS.remove(id);
    }

    /** Plays a named effect in the world the client is in; unknown ids are skipped (the client may lack the mod). */
    public static boolean playEffect(ResourceLocation id, VfxParams params) {
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

    /** Keeps the level's projection, as the level is about to be drawn with it. */
    public static void beginLevel() {
        CAMERA.projection.set(RenderSystem.getProjectionMatrix());
    }

    /** Draws every world effect. Called with the level drawn and its depth still there, before the hand. */
    public static void render(DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (error != null || level == null || (count == 0 && SHAPES.isEmpty() && RENDERERS.isEmpty())) return;
        RenderSystem.assertOnRenderThread();
        try {
            // (Paused, the game holds the part of a tick it had reached, so everything stays exactly where it was.)
            float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
            CAMERA.set(minecraft.gameRenderer.getMainCamera(), minecraft.gameRenderer.getDepthFar());
            FRAME.level = level;
            FRAME.partialTick = partialTick;
            // Its clock stops while the game is paused: renderers that animate by it, and shaders by their Time, stand still.
            FRAME.time = (float) (GameClock.seconds() % 3600.0);
            gatherParticles(partialTick);
            gatherShapes(partialTick);
            for (Map.Entry<ResourceLocation, VfxRenderer> renderer : RENDERERS.entrySet()) {
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
            int color = FastColor.ARGB32.color(Math.round(opacity * 255.0F), color(particle, progress));
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
            int color = FastColor.ARGB32.color(Math.round(FastColor.ARGB32.alpha(shape.argb) * fade), shape.argb);
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
        for (int phase = 0; phase < 2; phase++) {
            for (VfxBatch batch : BATCHES.values()) {
                if (batch.quads() > 0 && (batch.material.blend() == VfxBlend.ADDITIVE) == (phase == 1)) DRAWN.add(batch);
            }
        }
        if (DRAWN.isEmpty()) return;
        // Programs are compiled before any state is touched, so a shader that fails leaves the frame as it was.
        ShaderProgram[] programs = new ShaderProgram[DRAWN.size()];
        for (int i = 0; i < programs.length; i++) programs[i] = program(DRAWN.get(i).material);

        // The main target can't be read while it's drawn to: its depth is copied first. The copy unbinds it.
        RenderTarget main = minecraft.getMainRenderTarget();
        TextureTarget depth = worldDepth(main);
        depth.copyDepthFrom(main);
        main.bindWrite(false);
        TextureManager textures = minecraft.getTextureManager();
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        try {
            RenderSystem.activeTexture(GL13.GL_TEXTURE1);
            RenderSystem.bindTexture(depth.getDepthTextureId());
            for (int i = 0; i < programs.length; i++) {
                VfxBatch batch = DRAWN.get(i);
                VfxMaterial material = batch.material;
                ShaderProgram program = programs[i];
                program.use();
                program.setMatrix("ProjMat", CAMERA.projection);
                program.setMatrix("VfxViewMat", CAMERA.view);
                program.setVec4("VfxDepth", NEAR_PLANE, CAMERA.far, material.softness(), material.blend().luminanceAlpha() ? 1.0F : 0.0F);
                program.setVec4("VfxTime", FRAME.time, 0.0F, 0.0F, 0.0F);
                program.setSampler("Sampler0", 0);
                program.setSampler("DepthSampler", 1);
                if (material.blend() == VfxBlend.ADDITIVE) {
                    RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
                } else {
                    RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
                }
                RenderSystem.activeTexture(GL13.GL_TEXTURE0);
                RenderSystem.bindTexture(textures.getTexture(material.texture()).getId());
                // The texture may be shared (a block atlas, say): its filtering is put back once the batch is drawn.
                int minFilter = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
                int magFilter = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER);
                int filter = material.smooth() ? GL11.GL_LINEAR : GL11.GL_NEAREST;
                RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
                RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
                BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                batch.emit(builder);
                BufferUploader.draw(builder.buildOrThrow());
                RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, minFilter);
                RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, magFilter);
            }
        } finally {
            for (int unit = 1; unit >= 0; unit--) {
                RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit);
                RenderSystem.bindTexture(0);
            }
            ShaderProgram.release();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
    }

    // One program per fragment shader: the blend is GL state, set for each batch.
    private static ShaderProgram program(VfxMaterial material) {
        ResourceLocation fragment = material.shader() == null ? SHADER : material.shader();
        ShaderProgram cached = PROGRAMS.get(fragment);
        if (cached != null) return cached;
        ShaderProgram program;
        try {
            program = ShaderProgram.compile(Minecraft.getInstance().getResourceManager(), SHADER, fragment, List.of(), List.of("Position", "UV0", "Color"));
        } catch (IOException e) {
            throw new IllegalStateException("The world effects shader " + fragment + " failed to compile or link: " + e.getMessage(), e);
        }
        PROGRAMS.put(fragment, program);
        return program;
    }

    private static TextureTarget worldDepth(RenderTarget main) {
        if (worldDepth == null) {
            worldDepth = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        } else if (worldDepth.width != main.width || worldDepth.height != main.height) {
            worldDepth.resize(main.width, main.height, Minecraft.ON_OSX);
        }
        return worldDepth;
    }

    private static void fail(String message, RuntimeException e) {
        error = message + ": " + e.getMessage();
        CoolCatCanvas.LOGGER.error("{}; world effects are off until resources reload", message, e);
    }

    // ---- Lifecycle ----

    /** After shaders reload: programs are compiled again on next use, and a failure gets another chance. */
    public static void reload() {
        PROGRAMS.values().forEach(ShaderProgram::close);
        PROGRAMS.clear();
        error = null;
    }

    public static void close() {
        reload();
        if (worldDepth != null) {
            worldDepth.destroyBuffers();
            worldDepth = null;
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
