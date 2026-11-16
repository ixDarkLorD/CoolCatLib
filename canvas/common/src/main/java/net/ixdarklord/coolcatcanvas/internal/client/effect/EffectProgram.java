package net.ixdarklord.coolcatcanvas.internal.client.effect;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.ixdarklord.coolcatcanvas.api.client.effect.ScreenEffectDefinition;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.PostChainConfig;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Function;

// A definition compiled for one effect: shared pipelines, and uniform buffers of its own. Mirrors vanilla's PostChain,
// except uniforms are written every frame through ring buffers instead of baked in once.
final class EffectProgram implements AutoCloseable {
    private static final Identifier BLEND_FRAGMENT_SHADER = CoolCatCanvas.rl("post/strength_blend");
    private static final Identifier BLEND_ORIGINAL = CoolCatCanvas.rl("auto_blend/original");
    private static final Identifier BLEND_OUTPUT = CoolCatCanvas.rl("auto_blend/output");
    private static final int USAGE = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE;
    private static final int EFFECT_INFO_SIZE = new Std140SizeCalculator().putFloat().putFloat().putFloat().putFloat().get();
    private static final int VEC2_SIZE = new Std140SizeCalculator().putVec2().get();

    private final ScreenEffectDefinition definition;
    private final List<CompiledPass> passes;
    private final Map<Identifier, RenderTarget> persistentTargets = new HashMap<>();

    private EffectProgram(ScreenEffectDefinition definition, List<CompiledPass> passes) {
        this.definition = definition;
        this.passes = passes;
    }

    /**
     * @param pipelines builds or reuses a pipeline for a description, compiling it
     */
    static EffectProgram compile(Identifier effectId, ScreenEffectDefinition definition, boolean autoBlend, Function<PipelineKey, RenderPipeline> pipelines) throws ShaderManager.CompilationException {
        if (autoBlend) definition = withAutoBlend(definition);

        List<CompiledPass> passes = new ArrayList<>(definition.passes().size());
        try {
            for (int i = 0; i < definition.passes().size(); i++) {
                ScreenEffectDefinition.Pass pass = definition.passes().get(i);
                for (PostChainConfig.Input input : pass.inputs()) {
                    if (input instanceof PostChainConfig.TargetInput target && target.targetId().equals(pass.output()) && !target.useDepthBuffer()) {
                        throw new ShaderManager.CompilationException("Pass " + i + " reads the target it writes, " + pass.output() + "; draw into another target and copy it back");
                    }
                }
                RenderPipeline pipeline = pipelines.apply(PipelineKey.of(pass, effectId.withSuffix("/" + i)));
                passes.add(new CompiledPass(effectId + " #" + i, pipeline, pass));
            }
        } catch (ShaderManager.CompilationException | RuntimeException e) {
            passes.forEach(CompiledPass::close);
            throw e;
        }
        return new EffectProgram(definition, passes);
    }

    // Copies the frame aside first, then mixes it back with the processed one by strength.
    private static ScreenEffectDefinition withAutoBlend(ScreenEffectDefinition definition) {
        Map<Identifier, PostChainConfig.InternalTarget> targets = new HashMap<>(definition.targets());
        PostChainConfig.InternalTarget screenSized = new PostChainConfig.InternalTarget(Optional.empty(), Optional.empty(), false, 0);
        targets.put(BLEND_ORIGINAL, screenSized);
        targets.put(BLEND_OUTPUT, screenSized);

        List<ScreenEffectDefinition.Pass> passes = new ArrayList<>();
        passes.add(blit(ScreenEffectDefinition.MAIN_TARGET, BLEND_ORIGINAL));
        passes.addAll(definition.passes());
        passes.add(new ScreenEffectDefinition.Pass(ScreenEffectDefinition.SCREENQUAD_VERTEX_SHADER, BLEND_FRAGMENT_SHADER,
                List.of(new PostChainConfig.TargetInput("Original", BLEND_ORIGINAL, false, false), new PostChainConfig.TargetInput("Processed", ScreenEffectDefinition.MAIN_TARGET, false, false)),
                BLEND_OUTPUT, Map.of()));
        passes.add(blit(BLEND_OUTPUT, ScreenEffectDefinition.MAIN_TARGET));
        return new ScreenEffectDefinition(targets, passes);
    }

    private static ScreenEffectDefinition.Pass blit(Identifier from, Identifier to) {
        return new ScreenEffectDefinition.Pass(ScreenEffectDefinition.SCREENQUAD_VERTEX_SHADER, ScreenEffectDefinition.BLIT_FRAGMENT_SHADER,
                List.of(new PostChainConfig.TargetInput("In", from, false, false)), to, Map.of());
    }

    ScreenEffectDefinition definition() {
        return this.definition;
    }

    /** Adds the passes, returning the main target's handle after them. */
    ResourceHandle<RenderTarget> addToFrame(FrameGraphBuilder frame, ResourceHandle<RenderTarget> main, int width, int height, ScreenEffectImpl effect, FrameInfo info) {
        Map<Identifier, ResourceHandle<RenderTarget>> targets = new HashMap<>();
        targets.put(ScreenEffectDefinition.MAIN_TARGET, main);

        this.definition.targets().forEach((id, target) -> {
            RenderTargetDescriptor descriptor = new RenderTargetDescriptor(target.width().orElse(width), target.height().orElse(height), true, target.clearColor());
            if (target.persistent()) {
                targets.put(id, frame.importExternal(effect.id() + "/" + id, this.persistentTarget(id, descriptor)));
            } else {
                targets.put(id, frame.createInternal(effect.id() + "/" + id, descriptor));
            }
        });

        for (CompiledPass pass : this.passes) pass.addToFrame(frame, targets, effect, info);
        return targets.get(ScreenEffectDefinition.MAIN_TARGET);
    }

    private RenderTarget persistentTarget(Identifier id, RenderTargetDescriptor descriptor) {
        RenderTarget target = this.persistentTargets.get(id);
        if (target == null || target.width != descriptor.width() || target.height != descriptor.height()) {
            if (target != null) target.destroyBuffers();
            target = descriptor.allocate();
            descriptor.prepare(target);
            this.persistentTargets.put(id, target);
        }
        return target;
    }

    @Override
    public void close() {
        this.persistentTargets.values().forEach(RenderTarget::destroyBuffers);
        this.persistentTargets.clear();
        this.passes.forEach(CompiledPass::close);
    }

    /** What every pass of a frame writes into {@code EffectInfo}, besides the effect's own strength and age. */
    record FrameInfo(float time, float seed) {}

    /** Everything a pipeline depends on, so passes with the same shaders and bindings share one. */
    record PipelineKey(Identifier location, Identifier vertexShader, Identifier fragmentShader, List<String> samplers, List<String> uniformBlocks) {
        static PipelineKey of(ScreenEffectDefinition.Pass pass, Identifier location) {
            return new PipelineKey(location, pass.vertexShader(), pass.fragmentShader(),
                    pass.inputs().stream().map(PostChainConfig.Input::samplerName).toList(),
                    pass.uniforms().entrySet().stream().filter(block -> !block.getValue().isEmpty()).map(Map.Entry::getKey).toList());
        }

        /** The cache key: the same pipeline whatever effect asked first. */
        String cacheKey() {
            return this.vertexShader + "|" + this.fragmentShader + "|" + this.samplers + "|" + this.uniformBlocks;
        }

        RenderPipeline build() {
            RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                    .withVertexShader(this.vertexShader)
                    .withFragmentShader(this.fragmentShader)
                    .withLocation(this.location);
            for (String sampler : this.samplers) builder.withSampler(sampler + "Sampler");
            builder.withUniform("SamplerInfo", UniformType.UNIFORM_BUFFER);
            builder.withUniform("EffectInfo", UniformType.UNIFORM_BUFFER);
            for (String block : this.uniformBlocks) builder.withUniform(block, UniformType.UNIFORM_BUFFER);
            return builder.build();
        }
    }

    private static final class CompiledPass implements AutoCloseable {
        private final String label;
        private final RenderPipeline pipeline;
        private final Identifier output;
        private final List<PostPass.Input> inputs;
        private final List<UniformBlock> blocks = new ArrayList<>();
        private final MappableRingBuffer samplerInfo;
        private final MappableRingBuffer effectInfo;

        CompiledPass(String label, RenderPipeline pipeline, ScreenEffectDefinition.Pass pass) {
            this.label = label;
            this.pipeline = pipeline;
            this.output = pass.output();
            this.inputs = pass.inputs().stream().map(CompiledPass::input).toList();
            this.samplerInfo = new MappableRingBuffer(() -> label + " SamplerInfo", USAGE, (this.inputs.size() + 1) * VEC2_SIZE);
            this.effectInfo = new MappableRingBuffer(() -> label + " EffectInfo", USAGE, EFFECT_INFO_SIZE);
            pass.uniforms().forEach((name, specs) -> {
                if (specs.isEmpty()) return;
                Std140SizeCalculator calculator = new Std140SizeCalculator();
                for (ScreenEffectDefinition.UniformSpec spec : specs) spec.value().addSize(calculator);
                this.blocks.add(new UniformBlock(name, specs, new MappableRingBuffer(() -> label + " " + name, USAGE, calculator.get())));
            });
        }

        private static PostPass.Input input(PostChainConfig.Input input) {
            return switch (input) {
                case PostChainConfig.TargetInput target -> new PostPass.TargetInput(target.samplerName(), target.targetId(), target.useDepthBuffer(), target.bilinear());
                case PostChainConfig.TextureInput texture -> {
                    AbstractTexture loaded = Minecraft.getInstance().getTextureManager().getTexture(texture.location().withPath(path -> "textures/effect/" + path + ".png"));
                    yield new PostPass.TextureInput(texture.samplerName(), loaded, texture.width(), texture.height(), texture.bilinear());
                }
            };
        }

        void addToFrame(FrameGraphBuilder frame, Map<Identifier, ResourceHandle<RenderTarget>> targets, ScreenEffectImpl effect, FrameInfo info) {
            FramePass pass = frame.addPass(this.label);
            for (PostPass.Input input : this.inputs) input.addToPass(pass, targets);
            ResourceHandle<RenderTarget> output = targets.computeIfPresent(this.output, (id, handle) -> pass.readsAndWrites(handle));
            if (output == null) throw new IllegalStateException("Missing handle for target " + this.output);

            pass.executes(() -> {
                if (!effect.isLoaded()) return; // An earlier pass of the effect failed this frame.
                try {
                    this.execute(output.get(), targets, effect, info);
                } catch (RuntimeException e) {
                    effect.fail("Failed to draw pass " + this.label, e);
                }
            });
        }

        private void execute(RenderTarget output, Map<Identifier, ResourceHandle<RenderTarget>> targets, ScreenEffectImpl effect, FrameInfo info) {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            List<GpuTextureView> views = new ArrayList<>(this.inputs.size());
            List<GpuSampler> samplers = new ArrayList<>(this.inputs.size());
            for (PostPass.Input input : this.inputs) {
                views.add(input.texture(targets));
                samplers.add(RenderSystem.getSamplerCache().getClampToEdge(input.bilinear() ? FilterMode.LINEAR : FilterMode.NEAREST));
            }

            try (GpuBuffer.MappedView view = encoder.mapBuffer(this.samplerInfo.currentBuffer(), false, true)) {
                Std140Builder builder = Std140Builder.intoBuffer(view.data());
                builder.putVec2(output.width, output.height);
                for (GpuTextureView input : views) builder.putVec2(input.getWidth(0), input.getHeight(0));
            }
            try (GpuBuffer.MappedView view = encoder.mapBuffer(this.effectInfo.currentBuffer(), false, true)) {
                Std140Builder.intoBuffer(view.data())
                        .putFloat(effect.strength())
                        .putFloat(info.time())
                        .putFloat(effect.age())
                        .putFloat(info.seed());
            }
            for (UniformBlock block : this.blocks) {
                try (GpuBuffer.MappedView view = encoder.mapBuffer(block.buffer.currentBuffer(), false, true)) {
                    Std140Builder builder = Std140Builder.intoBuffer(view.data());
                    for (ScreenEffectDefinition.UniformSpec spec : block.specs) {
                        EffectUniformImpl uniform = effect.uniformIfSet(spec.name());
                        if (uniform != null) uniform.write(builder, spec.value().type());
                        else spec.value().writeTo(builder);
                    }
                }
            }

            // No depth attachment: a pass may sample main's depth while drawing into main's color.
            try (RenderPass renderPass = encoder.createRenderPass(() -> "Screen effect " + this.label, output.getColorTextureView(), OptionalInt.empty(), null, OptionalDouble.empty())) {
                renderPass.setPipeline(this.pipeline);
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.setUniform("SamplerInfo", this.samplerInfo.currentBuffer());
                renderPass.setUniform("EffectInfo", this.effectInfo.currentBuffer());
                for (UniformBlock block : this.blocks) renderPass.setUniform(block.name, block.buffer.currentBuffer());
                for (int i = 0; i < this.inputs.size(); i++) {
                    renderPass.bindTexture(this.inputs.get(i).samplerName() + "Sampler", views.get(i), samplers.get(i));
                }
                renderPass.draw(0, 3);
            }

            this.samplerInfo.rotate();
            this.effectInfo.rotate();
            for (UniformBlock block : this.blocks) block.buffer.rotate();
            for (PostPass.Input input : this.inputs) input.cleanup(targets);
        }

        @Override
        public void close() {
            this.samplerInfo.close();
            this.effectInfo.close();
            for (UniformBlock block : this.blocks) block.buffer.close();
        }

        private record UniformBlock(String name, List<ScreenEffectDefinition.UniformSpec> specs, MappableRingBuffer buffer) {}
    }
}
