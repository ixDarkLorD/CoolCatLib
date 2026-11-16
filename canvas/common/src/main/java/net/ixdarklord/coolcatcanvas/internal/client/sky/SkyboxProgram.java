package net.ixdarklord.coolcatcanvas.internal.client.sky;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.ixdarklord.coolcatcanvas.api.client.sky.SkyLayerDefinition;
import net.ixdarklord.coolcatcanvas.api.client.sky.SkyLayerStage;
import net.ixdarklord.coolcatcanvas.api.client.sky.SkyLayerType;
import net.ixdarklord.coolcatcanvas.api.client.sky.SkyboxDefinition;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.function.Supplier;

// A skybox definition compiled: each layer's pipeline and sampler resolved once, so a frame only fills uniforms.
final class SkyboxProgram {
    private static final float CUBE_SIZE = 50.0F;
    private static final float SPRITE_DISTANCE = 100.0F;
    private static final float MIN_ALPHA = 1.0E-3F;
    private static final double WRAP_SECONDS = 3600.0;

    private final CompiledLayer[] layers;

    private SkyboxProgram(CompiledLayer[] layers) {
        this.layers = layers;
    }

    /** Compiles the definition's layers, binding each to the skybox's handle of the same name. */
    static SkyboxProgram compile(SkyboxDefinition definition, SkyboxImpl skybox) {
        TextureManager textures = Minecraft.getInstance().getTextureManager();
        CompiledLayer[] layers = new CompiledLayer[definition.layers().size()];
        for (int i = 0; i < layers.length; i++) {
            SkyLayerDefinition layer = definition.layers().get(i);
            Identifier fragmentShader = layer.fragmentShader().orElseGet(() -> builtinShader(layer.type()));
            RenderPipeline pipeline = SkyResources.pipeline(new SkyResources.PipelineKey(fragmentShader, layer.blend(), layer.texture().isPresent()));
            // Loads the texture now rather than on the first frame it's drawn.
            layer.texture().ifPresent(textures::getTexture);
            SkyLayerImpl handle = skybox.handle(layer.name());
            handle.bind(layer);
            layers[i] = new CompiledLayer(skybox, layer, handle, pipeline, layer.texture().isPresent() ? sampler(layer) : null);
        }
        return new SkyboxProgram(layers);
    }

    boolean hasLayers() {
        return this.layers.length > 0;
    }

    /** Adds this stage's layers that show this frame to the list, their uniforms filled in. */
    void collect(SkyLayerStage stage, SkyboxImpl skybox, SkyFrame frame, Matrix4fc view, DrawList list) {
        for (CompiledLayer layer : this.layers) {
            if (layer.definition.stage() == stage && layer.prepare(skybox.visibility(), frame, view)) list.add(layer);
        }
    }

    private static Identifier builtinShader(SkyLayerType type) {
        return switch (type) {
            case CUBEMAP -> CoolCatCanvas.rl("sky/cubemap");
            case PANORAMA -> CoolCatCanvas.rl("sky/panorama");
            case SPRITE -> CoolCatCanvas.rl("sky/sprite");
            case GRADIENT -> CoolCatCanvas.rl("sky/gradient");
            case SHADER -> throw new IllegalStateException("A shader sky layer needs a fragment shader");
        };
    }

    private static GpuSampler sampler(SkyLayerDefinition layer) {
        FilterMode filter = layer.blur() ? FilterMode.LINEAR : FilterMode.NEAREST;
        // Panoramas wrap around horizontally; cubemap faces and sprites keep their own edges.
        AddressMode u = layer.type() == SkyLayerType.PANORAMA || layer.type() == SkyLayerType.SHADER ? AddressMode.REPEAT : AddressMode.CLAMP_TO_EDGE;
        AddressMode v = layer.type() == SkyLayerType.SHADER ? AddressMode.REPEAT : AddressMode.CLAMP_TO_EDGE;
        return RenderSystem.getSamplerCache().getSampler(u, v, filter, filter, false);
    }

    static final class CompiledLayer {
        final SkyboxImpl owner;
        final SkyLayerDefinition definition;
        final SkyLayerImpl handle;
        final RenderPipeline pipeline;
        final @Nullable GpuSampler sampler;
        final LayerUniform uniform = new LayerUniform();
        private final float yaw;
        private final float pitch;
        private final float roll;
        private final float spriteHalfSize;

        CompiledLayer(SkyboxImpl owner, SkyLayerDefinition definition, SkyLayerImpl handle, RenderPipeline pipeline, @Nullable GpuSampler sampler) {
            this.owner = owner;
            this.definition = definition;
            this.handle = handle;
            this.pipeline = pipeline;
            this.sampler = sampler;
            Vector3fc orientation = definition.orientation();
            this.yaw = orientation.y() * Mth.DEG_TO_RAD;
            this.pitch = orientation.x() * Mth.DEG_TO_RAD;
            this.roll = orientation.z() * Mth.DEG_TO_RAD;
            this.spriteHalfSize = SPRITE_DISTANCE * (float) Math.tan(definition.size() * 0.5F * Mth.DEG_TO_RAD);
        }

        /** Fills the uniform for this frame; false if the layer doesn't show. */
        boolean prepare(float skyboxVisibility, SkyFrame frame, Matrix4fc view) {
            if (!this.handle.isVisible()) return false;
            LayerUniform u = this.uniform;
            SkyLayerDefinition definition = this.definition;

            this.handle.tint(u.color);
            float alpha = u.color[3] * this.handle.alpha() * skyboxVisibility;
            if (definition.dayFade().isPresent()) alpha *= definition.dayFade().get().visibility(frame.dayTime);
            if (definition.rainFade() > 0.0F) alpha *= 1.0F - definition.rainFade() * Math.max(frame.rain, frame.thunder);
            if (alpha <= MIN_ALPHA) return false;
            u.color[3] = Math.min(alpha, 1.0F);

            u.view.set(view);
            u.model.identity();
            if (definition.rotation().isPresent()) {
                SkyLayerDefinition.Rotation rotation = definition.rotation().get();
                float angle = (float) ((rotation.speed() * frame.read(rotation.clock())) % 360.0) * Mth.DEG_TO_RAD;
                u.model.rotate(angle, rotation.axis().x(), rotation.axis().y(), rotation.axis().z());
            }
            u.model.rotateY(this.yaw).rotateX(this.pitch).rotateZ(this.roll);
            if (definition.type().isSprite()) u.model.translate(0.0F, SPRITE_DISTANCE, 0.0F).scale(this.spriteHalfSize, 1.0F, this.spriteHalfSize);
            else u.model.scale(CUBE_SIZE);

            if (definition.animation().isPresent()) {
                SkyLayerDefinition.Animation animation = definition.animation().get();
                double position = frame.read(animation.clock()) * 20.0 / animation.frameTime();
                double whole = Math.floor(position);
                int current = Math.floorMod((long) whole, animation.frames());
                u.frame[0] = current;
                u.frame[1] = (current + 1) % animation.frames();
                u.frame[2] = animation.interpolate() ? (float) (position - whole) : 0.0F;
                u.frame[3] = animation.frames();
            } else {
                u.frame[0] = 0.0F;
                u.frame[1] = 0.0F;
                u.frame[2] = 0.0F;
                u.frame[3] = 1.0F;
            }

            u.time[0] = (float) (frame.realSeconds % WRAP_SECONDS);
            u.time[1] = (float) (frame.gameSeconds % WRAP_SECONDS);
            u.time[2] = frame.dayTime / SkyLayerDefinition.DayFade.DAY_LENGTH;
            u.time[3] = frame.sunAngle * Mth.DEG_TO_RAD;

            u.env[0] = frame.rain;
            u.env[1] = frame.thunder;
            u.env[2] = definition.fog();
            u.env[3] = skyboxVisibility;

            for (int i = 0; i < SkyLayerDefinition.MAX_PARAMS; i++) this.handle.param(i, u.params, i * 4);
            return true;
        }

        void draw(RenderPass pass, GpuBufferSlice uniform, @Nullable GpuTextureView texture) {
            pass.setPipeline(this.pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform(LayerUniform.BLOCK, uniform);
            if (texture != null) pass.bindTexture("Sampler0", texture, this.sampler);
            pass.setVertexBuffer(0, SkyResources.mesh());
            if (this.definition.type().isSprite()) pass.draw(SkyResources.SPRITE_FIRST_VERTEX, SkyResources.SPRITE_VERTICES);
            else pass.draw(SkyResources.CUBE_FIRST_VERTEX, SkyResources.CUBE_VERTICES);
        }
    }

    // The layers of one stage about to be drawn, gathered first because uniforms can't be written once the render
    // pass is open. Reused every frame; it only grows.
    static final class DrawList {
        private CompiledLayer[] layers = new CompiledLayer[8];
        private @Nullable GpuTextureView[] textureViews = new GpuTextureView[8];
        private LayerUniform[] uniforms = new LayerUniform[0];
        private int size;

        void add(CompiledLayer layer) {
            if (this.size == this.layers.length) {
                this.layers = Arrays.copyOf(this.layers, this.size * 2);
                this.textureViews = Arrays.copyOf(this.textureViews, this.size * 2);
            }
            this.layers[this.size++] = layer;
        }

        boolean isEmpty() {
            return this.size == 0;
        }

        void clear() {
            Arrays.fill(this.layers, 0, this.size, null);
            Arrays.fill(this.textureViews, 0, this.size, null);
            this.size = 0;
        }

        /** Writes every uniform in one go, resolves textures, then draws them all in one render pass. */
        void drawAll(Supplier<RenderPass> passes) {
            TextureManager textureManager = Minecraft.getInstance().getTextureManager();
            if (this.uniforms.length != this.size) this.uniforms = new LayerUniform[this.size];
            for (int i = 0; i < this.size; i++) {
                CompiledLayer layer = this.layers[i];
                this.uniforms[i] = layer.uniform;
                // A lookup, unless the texture was released since: it's loaded again here, before the pass opens.
                this.textureViews[i] = layer.definition.texture().isPresent() ? textureManager.getTexture(layer.definition.texture().get()).getTextureView() : null;
            }
            GpuBufferSlice[] slices = SkyResources.uniforms().writeUniforms(this.uniforms);
            try (RenderPass pass = passes.get()) {
                for (int i = 0; i < this.size; i++) {
                    CompiledLayer layer = this.layers[i];
                    if (layer.owner.error() != null) continue;
                    try {
                        layer.draw(pass, slices[i], this.textureViews[i]);
                    } catch (RuntimeException e) {
                        layer.owner.fail("Failed to draw layer " + layer.definition.name(), e);
                    }
                }
            }
        }
    }
}
