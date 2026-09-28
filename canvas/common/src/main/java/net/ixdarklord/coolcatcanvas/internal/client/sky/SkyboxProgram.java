package net.ixdarklord.coolcatcanvas.internal.client.sky;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.ixdarklord.coolcatcanvas.api.client.sky.SkyLayerDefinition;
import net.ixdarklord.coolcatcanvas.api.client.sky.SkyLayerStage;
import net.ixdarklord.coolcatcanvas.api.client.sky.SkyLayerType;
import net.ixdarklord.coolcatcanvas.api.client.sky.SkyboxDefinition;
import net.ixdarklord.coolcatcanvas.internal.client.render.CanvasProgram;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;

import java.io.IOException;

// A skybox definition compiled: each layer's program resolved once, so a frame only fills uniforms and draws.
final class SkyboxProgram {
    private static final float CUBE_SIZE = 50.0F;
    private static final float SPRITE_DISTANCE = 100.0F;
    private static final float MIN_ALPHA = 1.0E-3F;
    private static final double WRAP_SECONDS = 3600.0;
    private static final int GL_TEXTURE0 = 33984;
    private static final int GL_TEXTURE_2D = 3553;
    private static final int GL_TEXTURE_WRAP_S = 10242;
    private static final int GL_TEXTURE_WRAP_T = 10243;
    private static final int GL_REPEAT = 10497;
    private static final int GL_CLAMP_TO_EDGE = 33071;

    private final CompiledLayer[] layers;

    private SkyboxProgram(CompiledLayer[] layers) {
        this.layers = layers;
    }

    /** Compiles the definition's layers, binding each to the skybox's handle of the same name. */
    static SkyboxProgram compile(SkyboxDefinition definition, SkyboxImpl skybox) throws IOException {
        TextureManager textures = Minecraft.getInstance().getTextureManager();
        CompiledLayer[] layers = new CompiledLayer[definition.layers().size()];
        for (int i = 0; i < layers.length; i++) {
            SkyLayerDefinition layer = definition.layers().get(i);
            ResourceLocation fragmentShader = layer.fragmentShader().orElseGet(() -> builtinShader(layer.type()));
            CanvasProgram program = SkyResources.program(new SkyResources.ProgramKey(fragmentShader, layer.blend(), layer.texture().isPresent()));
            // Loads the texture now rather than on the first frame it's drawn.
            layer.texture().ifPresent(textures::getTexture);
            SkyLayerImpl handle = skybox.handle(layer.name());
            handle.bind(layer);
            layers[i] = new CompiledLayer(skybox, layer, handle, program);
        }
        return new SkyboxProgram(layers);
    }

    boolean hasLayers() {
        return this.layers.length > 0;
    }

    /** Draws this stage's layers that show this frame. GL state is left for the caller to restore. */
    void draw(SkyLayerStage stage, SkyboxImpl skybox, SkyFrame frame, Matrix4fc view, Matrix4fc projection, SkyFog fog) {
        for (CompiledLayer layer : this.layers) {
            if (layer.definition.stage() != stage || skybox.error() != null) continue;
            try {
                if (layer.prepare(skybox.visibility(), frame, view)) layer.draw(projection, fog);
            } catch (RuntimeException e) {
                skybox.fail("Failed to draw layer " + layer.definition.name(), e);
            }
        }
    }

    private static ResourceLocation builtinShader(SkyLayerType type) {
        return switch (type) {
            case CUBEMAP -> CoolCatCanvas.rl("sky/cubemap");
            case PANORAMA -> CoolCatCanvas.rl("sky/panorama");
            case SPRITE -> CoolCatCanvas.rl("sky/sprite");
            case GRADIENT -> CoolCatCanvas.rl("sky/gradient");
            case SHADER -> throw new IllegalStateException("A shader sky layer needs a fragment shader");
        };
    }

    /** Vanilla's sky fog as the sky pass set it up, read once per stage. */
    record SkyFog(float start, float end, float[] color, int shape) {
        static SkyFog current() {
            return new SkyFog(RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(), RenderSystem.getShaderFogColor().clone(), RenderSystem.getShaderFogShape().getIndex());
        }
    }

    static final class CompiledLayer {
        final SkyboxImpl owner;
        final SkyLayerDefinition definition;
        final SkyLayerImpl handle;
        final CanvasProgram program;
        final LayerUniform uniform = new LayerUniform();
        private final float yaw;
        private final float pitch;
        private final float roll;
        private final float spriteHalfSize;

        CompiledLayer(SkyboxImpl owner, SkyLayerDefinition definition, SkyLayerImpl handle, CanvasProgram program) {
            this.owner = owner;
            this.definition = definition;
            this.handle = handle;
            this.program = program;
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

        void draw(Matrix4fc projection, SkyFog fog) {
            CanvasProgram program = this.program;
            program.use();
            this.uniform.upload(program);
            program.setMat4("ProjMat", projection);
            program.setFloat("FogStart", fog.start());
            program.setFloat("FogEnd", fog.end());
            program.setVec4("FogColor", fog.color());
            program.setInt("FogShape", fog.shape());

            boolean textured = this.definition.texture().isPresent();
            if (textured) {
                // A lookup, unless the texture was released since: it's loaded again here.
                AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(this.definition.texture().get());
                RenderSystem.activeTexture(GL_TEXTURE0);
                texture.setFilter(this.definition.blur(), false);
                RenderSystem.bindTexture(texture.getId());
                // Panoramas wrap around horizontally; cubemap faces and sprites keep their own edges.
                SkyLayerType type = this.definition.type();
                int u = type == SkyLayerType.PANORAMA || type == SkyLayerType.SHADER ? GL_REPEAT : GL_CLAMP_TO_EDGE;
                int v = type == SkyLayerType.SHADER ? GL_REPEAT : GL_CLAMP_TO_EDGE;
                GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, u);
                GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, v);
                program.setInt("Sampler0", 0);
            }
            this.definition.blend().apply();

            VertexBuffer mesh = this.definition.type().isSprite() ? SkyResources.sprite() : SkyResources.cube();
            mesh.bind();
            mesh.draw();
            VertexBuffer.unbind();
            if (textured) RenderSystem.bindTexture(0);
            CanvasProgram.unuse();
        }
    }
}
