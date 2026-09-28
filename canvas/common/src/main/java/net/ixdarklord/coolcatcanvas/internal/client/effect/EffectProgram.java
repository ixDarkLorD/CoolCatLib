package net.ixdarklord.coolcatcanvas.internal.client.effect;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.ixdarklord.coolcatcanvas.api.client.effect.ScreenEffectDefinition;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// A definition compiled for one effect: shared shader programs, the effect's own persistent targets, and every
// uniform written each time a pass draws. Mirrors vanilla's PostChain, except uniforms change every frame and targets
// that aren't persistent are borrowed from a pool shared by all effects.
final class EffectProgram implements AutoCloseable {
    private static final ResourceLocation BLEND_FRAGMENT_SHADER = CoolCatCanvas.rl("post/strength_blend");
    private static final ResourceLocation BLEND_ORIGINAL = CoolCatCanvas.rl("auto_blend/original");
    private static final ResourceLocation BLEND_OUTPUT = CoolCatCanvas.rl("auto_blend/output");
    private static final int TEXTURE_2D = 3553;
    private static final int TEXTURE0 = 33984;
    private static final int MIN_FILTER = 10241;
    private static final int MAG_FILTER = 10240;
    private static final int NEAREST = 9728;
    private static final int LINEAR = 9729;

    private final ScreenEffectDefinition definition;
    private final List<CompiledPass> passes;
    private final Map<ResourceLocation, RenderTarget> persistentTargets = new HashMap<>();

    private EffectProgram(ScreenEffectDefinition definition, List<CompiledPass> passes) {
        this.definition = definition;
        this.passes = passes;
    }

    /**
     * @param programs builds or reuses the program of a vertex and fragment shader
     */
    static EffectProgram compile(ResourceLocation effectId, ScreenEffectDefinition definition, boolean autoBlend, ProgramSource programs) throws IOException {
        if (autoBlend) definition = withAutoBlend(definition);

        List<CompiledPass> passes = new ArrayList<>(definition.passes().size());
        for (int i = 0; i < definition.passes().size(); i++) {
            ScreenEffectDefinition.Pass pass = definition.passes().get(i);
            for (ScreenEffectDefinition.Input input : pass.inputs()) {
                if (input instanceof ScreenEffectDefinition.TargetInput target && target.targetId().equals(pass.output()) && !target.useDepthBuffer()) {
                    throw new IOException("Pass " + i + " reads the target it writes, " + pass.output() + "; draw into another target and copy it back");
                }
            }
            ShaderProgram program = programs.get(ScreenEffectDefinition.resolveShader(pass.vertexShader()), ScreenEffectDefinition.resolveShader(pass.fragmentShader()));
            passes.add(new CompiledPass(effectId + " #" + i, program, pass));
        }
        return new EffectProgram(definition, passes);
    }

    // Copies the frame aside first, then mixes it back with the processed one by strength.
    private static ScreenEffectDefinition withAutoBlend(ScreenEffectDefinition definition) {
        Map<ResourceLocation, ScreenEffectDefinition.Target> targets = new HashMap<>(definition.targets());
        targets.put(BLEND_ORIGINAL, ScreenEffectDefinition.Target.SCREEN);
        targets.put(BLEND_OUTPUT, ScreenEffectDefinition.Target.SCREEN);

        List<ScreenEffectDefinition.Pass> passes = new ArrayList<>();
        passes.add(blit(ScreenEffectDefinition.MAIN_TARGET, BLEND_ORIGINAL));
        passes.addAll(definition.passes());
        passes.add(new ScreenEffectDefinition.Pass(ScreenEffectDefinition.SCREENQUAD_VERTEX_SHADER, BLEND_FRAGMENT_SHADER,
                List.of(new ScreenEffectDefinition.TargetInput("Original", BLEND_ORIGINAL, false, false), new ScreenEffectDefinition.TargetInput("Processed", ScreenEffectDefinition.MAIN_TARGET, false, false)),
                BLEND_OUTPUT, Map.of()));
        passes.add(blit(BLEND_OUTPUT, ScreenEffectDefinition.MAIN_TARGET));
        return new ScreenEffectDefinition(targets, passes);
    }

    private static ScreenEffectDefinition.Pass blit(ResourceLocation from, ResourceLocation to) {
        return new ScreenEffectDefinition.Pass(ScreenEffectDefinition.SCREENQUAD_VERTEX_SHADER, ScreenEffectDefinition.BLIT_FRAGMENT_SHADER,
                List.of(new ScreenEffectDefinition.TargetInput("In", from, false, false)), to, Map.of());
    }

    ScreenEffectDefinition definition() {
        return this.definition;
    }

    /** Draws every pass over the main target. GL state is set up by the caller. */
    void draw(RenderTarget main, ScreenEffectImpl effect, FrameInfo info, TargetPool pool) {
        Map<ResourceLocation, RenderTarget> targets = new HashMap<>();
        targets.put(ScreenEffectDefinition.MAIN_TARGET, main);
        List<RenderTarget> borrowed = new ArrayList<>(this.definition.targets().size());
        try {
            this.definition.targets().forEach((id, target) -> {
                int width = target.width().orElse(main.width);
                int height = target.height().orElse(main.height);
                if (target.persistent()) {
                    targets.put(id, this.persistentTarget(id, width, height, target.clearColor()));
                } else {
                    RenderTarget pooled = pool.acquire(width, height);
                    borrowed.add(pooled);
                    clear(pooled, target.clearColor());
                    targets.put(id, pooled);
                }
            });

            for (CompiledPass pass : this.passes) {
                if (!effect.isLoaded()) return; // An earlier pass of the effect failed this frame.
                try {
                    pass.execute(targets, effect, info);
                } catch (RuntimeException e) {
                    effect.fail("Failed to draw pass " + pass.label, e);
                    return;
                }
            }
        } finally {
            borrowed.forEach(pool::release);
        }
    }

    private RenderTarget persistentTarget(ResourceLocation id, int width, int height, int clearColor) {
        RenderTarget target = this.persistentTargets.get(id);
        if (target == null || target.width != width || target.height != height) {
            if (target != null) target.destroyBuffers();
            target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            clear(target, clearColor);
            this.persistentTargets.put(id, target);
        }
        return target;
    }

    private static void clear(RenderTarget target, int argb) {
        target.setClearColor((argb >> 16 & 0xFF) / 255.0F, (argb >> 8 & 0xFF) / 255.0F, (argb & 0xFF) / 255.0F, (argb >>> 24) / 255.0F);
        target.clear(Minecraft.ON_OSX);
    }

    @Override
    public void close() {
        this.persistentTargets.values().forEach(RenderTarget::destroyBuffers);
        this.persistentTargets.clear();
    }

    /** What every pass of a frame sets besides the effect's own strength and age. */
    record FrameInfo(float time, float seed) {}

    /** Builds or reuses the program of a vertex and fragment shader. */
    @FunctionalInterface
    interface ProgramSource {
        ShaderProgram get(ResourceLocation vertexShader, ResourceLocation fragmentShader) throws IOException;
    }

    private static final class CompiledPass {
        private final String label;
        private final ShaderProgram program;
        private final ResourceLocation output;
        private final List<ScreenEffectDefinition.Input> inputs;
        private final @Nullable AbstractTexture[] textures;
        private final String[] samplerUniforms;
        private final String[] sizeUniforms;
        private final List<ScreenEffectDefinition.UniformSpec> uniforms = new ArrayList<>();
        private final List<float[]> defaults = new ArrayList<>();
        private final Matrix4f projection = new Matrix4f();

        CompiledPass(String label, ShaderProgram program, ScreenEffectDefinition.Pass pass) {
            this.label = label;
            this.program = program;
            this.output = pass.output();
            this.inputs = pass.inputs();
            int count = this.inputs.size();
            this.textures = new AbstractTexture[count];
            this.samplerUniforms = new String[count];
            this.sizeUniforms = new String[count];
            for (int i = 0; i < count; i++) {
                ScreenEffectDefinition.Input input = this.inputs.get(i);
                this.samplerUniforms[i] = input.samplerName() + "Sampler";
                this.sizeUniforms[i] = input.samplerName() + "Size";
                // Loads the texture now rather than on the first frame it's drawn.
                if (input instanceof ScreenEffectDefinition.TextureInput texture) {
                    this.textures[i] = Minecraft.getInstance().getTextureManager().getTexture(texture.location().withPath(path -> "textures/effect/" + path + ".png"));
                }
            }
            pass.uniforms().values().forEach(block -> block.forEach(spec -> {
                this.uniforms.add(spec);
                this.defaults.add(spec.value().components());
            }));
        }

        void execute(Map<ResourceLocation, RenderTarget> targets, ScreenEffectImpl effect, FrameInfo info) {
            RenderTarget output = targets.get(this.output);
            if (output == null) throw new IllegalStateException("Missing target " + this.output);
            ShaderProgram program = this.program;
            program.use();

            for (int i = 0; i < this.inputs.size(); i++) {
                RenderSystem.activeTexture(TEXTURE0 + i);
                int width;
                int height;
                switch (this.inputs.get(i)) {
                    case ScreenEffectDefinition.TargetInput input -> {
                        RenderTarget target = targets.get(input.targetId());
                        if (target == null) throw new IllegalStateException("Missing target " + input.targetId());
                        if (input.useDepthBuffer()) {
                            RenderSystem.bindTexture(target.getDepthTextureId());
                        } else {
                            target.setFilterMode(input.bilinear() ? LINEAR : NEAREST);
                            RenderSystem.bindTexture(target.getColorTextureId());
                        }
                        width = target.width;
                        height = target.height;
                    }
                    case ScreenEffectDefinition.TextureInput input -> {
                        AbstractTexture texture = this.textures[i];
                        RenderSystem.bindTexture(texture != null ? texture.getId() : 0);
                        int filter = input.bilinear() ? LINEAR : NEAREST;
                        RenderSystem.texParameter(TEXTURE_2D, MIN_FILTER, filter);
                        RenderSystem.texParameter(TEXTURE_2D, MAG_FILTER, filter);
                        width = input.width();
                        height = input.height();
                    }
                }
                program.setSampler(this.samplerUniforms[i], i);
                program.setVec2(this.sizeUniforms[i], width, height);
                // Vanilla's names: InSize for the first input, AuxSize<n> for the others.
                if (i == 0) program.setVec2("InSize", width, height);
                else program.setVec2("AuxSize" + (i - 1), width, height);
            }

            for (int i = 0; i < this.uniforms.size(); i++) {
                ScreenEffectDefinition.UniformSpec spec = this.uniforms.get(i);
                EffectUniformImpl uniform = effect.uniformIfSet(spec.name());
                program.set(spec.name(), spec.type(), uniform != null ? uniform.values() : this.defaults.get(i));
            }

            int width = output.width;
            int height = output.height;
            program.setFloat("Strength", effect.strength());
            program.setFloat("Time", info.time());
            program.setFloat("Age", effect.age());
            program.setFloat("Seed", info.seed());
            program.setMatrix("ProjMat", this.projection.setOrtho(0.0F, width, 0.0F, height, 0.1F, 1000.0F));
            program.setVec2("OutSize", width, height);
            Window window = Minecraft.getInstance().getWindow();
            program.setVec2("ScreenSize", window.getWidth(), window.getHeight());

            output.bindWrite(false);
            RenderSystem.viewport(0, 0, width, height);
            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            builder.addVertex(0.0F, 0.0F, 500.0F);
            builder.addVertex(width, 0.0F, 500.0F);
            builder.addVertex(width, height, 500.0F);
            builder.addVertex(0.0F, height, 500.0F);
            BufferUploader.draw(builder.buildOrThrow());

            for (int i = this.inputs.size() - 1; i >= 0; i--) {
                RenderSystem.activeTexture(TEXTURE0 + i);
                RenderSystem.bindTexture(0);
            }
        }
    }
}
