package net.ixdarklord.coolcatcanvas.internal.client.effect;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.ixdarklord.coolcatcanvas.api.client.effect.ScreenEffectDefinition;
import net.ixdarklord.coolcatcanvas.internal.client.render.CanvasProgram;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// A definition compiled for one effect: shared programs, and targets of its own. Mirrors vanilla's PostChain and
// PostPass, except uniforms are written every frame, main's depth is never cleared, and namespaced programs load.
final class EffectProgram implements AutoCloseable {
    private static final ResourceLocation BLEND_PROGRAM = CoolCatCanvas.rl("strength_blend");
    private static final ResourceLocation BLEND_ORIGINAL = CoolCatCanvas.rl("auto_blend/original");
    private static final ResourceLocation BLEND_OUTPUT = CoolCatCanvas.rl("auto_blend/output");
    private static final int GL_TEXTURE0 = 33984;
    private static final int GL_TEXTURE_2D = 3553;
    private static final int GL_TEXTURE_WIDTH = 4096;
    private static final int GL_TEXTURE_HEIGHT = 4097;
    private static final int GL_NEAREST = 9728;
    private static final int GL_LINEAR = 9729;
    private static final int GL_COLOR_BUFFER_BIT = 16384;
    private static final int GL_ALWAYS = 519;
    private static final int GL_LEQUAL = 515;

    private final ScreenEffectDefinition definition;
    private final List<CompiledPass> passes;
    private final Map<ResourceLocation, TargetSlot> targets;
    private final Matrix4f projection = new Matrix4f();

    private EffectProgram(ScreenEffectDefinition definition, List<CompiledPass> passes, Map<ResourceLocation, TargetSlot> targets) {
        this.definition = definition;
        this.passes = passes;
        this.targets = targets;
    }

    /**
     * @param shaders loads or reuses a program by id
     */
    static EffectProgram compile(ScreenEffectDefinition definition, boolean autoBlend, ShaderSource shaders) throws IOException {
        if (autoBlend) definition = withAutoBlend(definition);

        Map<ResourceLocation, TargetSlot> targets = new LinkedHashMap<>();
        definition.targets().forEach((id, target) -> targets.put(id, new TargetSlot(target)));
        List<CompiledPass> passes = new ArrayList<>(definition.passes().size());
        for (int i = 0; i < definition.passes().size(); i++) {
            ScreenEffectDefinition.Pass pass = definition.passes().get(i);
            EffectShader shader = shaders.get(pass.program());
            List<CompiledAux> aux = new ArrayList<>(pass.auxTargets().size());
            for (ScreenEffectDefinition.AuxTarget input : pass.auxTargets()) {
                boolean target = definition.isTarget(input.id());
                if (target && input.depth() && targets.containsKey(input.id())) targets.get(input.id()).needsDepth = true;
                aux.add(new CompiledAux(input, target ? null : new ResourceLocation(input.id().getNamespace(), "textures/effect/" + input.id().getPath() + ".png")));
            }
            Map<String, float[]> values = new HashMap<>();
            for (ScreenEffectDefinition.UniformSpec spec : pass.uniforms()) values.put(spec.name(), spec.toArray());
            passes.add(new CompiledPass(i, shader, pass, aux, values));
        }
        return new EffectProgram(definition, passes, targets);
    }

    // Copies the frame aside first, then mixes it back with the processed one by strength.
    private static ScreenEffectDefinition withAutoBlend(ScreenEffectDefinition definition) {
        Map<ResourceLocation, ScreenEffectDefinition.Target> targets = new LinkedHashMap<>(definition.targets());
        targets.put(BLEND_ORIGINAL, ScreenEffectDefinition.Target.SCREEN_SIZED);
        targets.put(BLEND_OUTPUT, ScreenEffectDefinition.Target.SCREEN_SIZED);

        List<ScreenEffectDefinition.Pass> passes = new ArrayList<>();
        passes.add(blit(ScreenEffectDefinition.MAIN_TARGET, BLEND_ORIGINAL));
        passes.addAll(definition.passes());
        passes.add(new ScreenEffectDefinition.Pass(BLEND_PROGRAM, ScreenEffectDefinition.MAIN_TARGET, BLEND_OUTPUT,
                List.of(ScreenEffectDefinition.AuxTarget.target("OriginalSampler", BLEND_ORIGINAL)), List.of(), false));
        passes.add(blit(BLEND_OUTPUT, ScreenEffectDefinition.MAIN_TARGET));
        return new ScreenEffectDefinition(targets, passes);
    }

    private static ScreenEffectDefinition.Pass blit(ResourceLocation from, ResourceLocation to) {
        return new ScreenEffectDefinition.Pass(ScreenEffectDefinition.BLIT_PROGRAM, from, to, List.of(), List.of(), false);
    }

    ScreenEffectDefinition definition() {
        return this.definition;
    }

    /**
     * Every uniform's default, from the first pass whose program uses it: that pass's value, else the program's
     * declared default. Built-in uniforms are left out.
     */
    Map<String, float[]> defaults() {
        Map<String, float[]> defaults = new HashMap<>();
        for (CompiledPass pass : this.passes) {
            for (EffectShader.UniformDecl uniform : pass.shader.uniforms.values()) {
                if (EffectShader.isBuiltin(uniform.name())) continue;
                float[] value = pass.values.get(uniform.name());
                defaults.putIfAbsent(uniform.name(), value != null ? value.clone() : uniform.defaults().clone());
            }
            // A value for a uniform the program doesn't declare is still the effect's to set.
            pass.values.forEach((name, value) -> {
                if (!EffectShader.isBuiltin(name)) defaults.putIfAbsent(name, value.clone());
            });
        }
        return defaults;
    }

    /** Draws every pass over {@code main}. GL state is left for the caller to restore. */
    void run(RenderTarget main, ScreenEffectImpl effect, FrameInfo info) {
        for (TargetSlot slot : this.targets.values()) slot.prepare(main.width, main.height);
        for (CompiledPass pass : this.passes) {
            if (!effect.isLoaded()) return; // An earlier pass failed this frame.
            try {
                pass.run(this, main, effect, info);
            } catch (RuntimeException e) {
                effect.fail("Failed to draw pass " + pass.index + " (" + pass.shader.id + ")", e);
            }
        }
    }

    private RenderTarget target(ResourceLocation id, RenderTarget main) {
        if (id.equals(ScreenEffectDefinition.MAIN_TARGET)) return main;
        TargetSlot slot = this.targets.get(id);
        if (slot == null || slot.target == null) throw new IllegalStateException("Missing target " + id);
        return slot.target;
    }

    @Override
    public void close() {
        for (TargetSlot slot : this.targets.values()) slot.close();
    }

    /** What every pass of a frame gets, besides the effect's own strength and age. */
    record FrameInfo(float time, float seed) {}

    /** Loads a program by id, or reuses one already loaded. */
    @FunctionalInterface
    interface ShaderSource {
        EffectShader get(ResourceLocation program) throws IOException;
    }

    // One of the definition's targets, allocated on the first frame and resized with the screen.
    private static final class TargetSlot implements AutoCloseable {
        private final ScreenEffectDefinition.Target definition;
        private boolean needsDepth;
        private @Nullable RenderTarget target;

        TargetSlot(ScreenEffectDefinition.Target definition) {
            this.definition = definition;
        }

        void prepare(int screenWidth, int screenHeight) {
            int width = this.definition.width().orElse(screenWidth);
            int height = this.definition.height().orElse(screenHeight);
            if (this.target == null) {
                this.target = new TextureTarget(width, height, this.needsDepth, Minecraft.ON_OSX);
                this.target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
                this.target.clear(Minecraft.ON_OSX);
            } else if (this.target.width != width || this.target.height != height) {
                this.target.resize(width, height, Minecraft.ON_OSX);
            }
        }

        @Override
        public void close() {
            if (this.target != null) {
                this.target.destroyBuffers();
                this.target = null;
            }
        }
    }

    private record CompiledAux(ScreenEffectDefinition.AuxTarget input, @Nullable ResourceLocation texture) {}

    private static final class CompiledPass {
        private final int index;
        private final EffectShader shader;
        private final ScreenEffectDefinition.Pass pass;
        private final List<CompiledAux> aux;
        private final Map<String, float[]> values;
        private final int[] auxTextures;
        private final int[] auxFilters;

        CompiledPass(int index, EffectShader shader, ScreenEffectDefinition.Pass pass, List<CompiledAux> aux, Map<String, float[]> values) {
            this.index = index;
            this.shader = shader;
            this.pass = pass;
            this.aux = aux;
            this.values = values;
            this.auxTextures = new int[aux.size()];
            this.auxFilters = new int[aux.size()];
        }

        void run(EffectProgram owner, RenderTarget main, ScreenEffectImpl effect, FrameInfo info) {
            RenderTarget in = owner.target(this.pass.input(), main);
            RenderTarget out = owner.target(this.pass.output(), main);
            int width = out.width;
            int height = out.height;

            // Filtering is a property of the texture: set it for this pass, and put it back after.
            int inFilter = in.filterMode;
            int wantedFilter = this.pass.bilinear() ? GL_LINEAR : GL_NEAREST;
            if (inFilter != wantedFilter) in.setFilterMode(wantedFilter);
            float[] auxSizes = new float[this.aux.size() * 2];
            for (int i = 0; i < this.aux.size(); i++) {
                CompiledAux aux = this.aux.get(i);
                this.auxFilters[i] = -1;
                if (aux.texture == null) {
                    RenderTarget target = owner.target(aux.input.id(), main);
                    if (aux.input.depth()) {
                        this.auxTextures[i] = target.getDepthTextureId();
                    } else {
                        this.auxTextures[i] = target.getColorTextureId();
                        int filter = aux.input.bilinear() ? GL_LINEAR : GL_NEAREST;
                        if (target.filterMode != filter) {
                            this.auxFilters[i] = target.filterMode;
                            target.setFilterMode(filter);
                        }
                    }
                    auxSizes[i * 2] = target.width;
                    auxSizes[i * 2 + 1] = target.height;
                } else {
                    AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(aux.texture);
                    texture.setFilter(aux.input.bilinear(), false);
                    this.auxTextures[i] = texture.getId();
                    int textureWidth = aux.input.width();
                    int textureHeight = aux.input.height();
                    if (textureWidth <= 0 || textureHeight <= 0) {
                        RenderSystem.bindTexture(texture.getId());
                        textureWidth = GlStateManager._getTexLevelParameter(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH);
                        textureHeight = GlStateManager._getTexLevelParameter(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
                    }
                    auxSizes[i * 2] = textureWidth;
                    auxSizes[i * 2 + 1] = textureHeight;
                }
            }

            CanvasProgram program = this.shader.program;
            program.use();
            RenderSystem.activeTexture(GL_TEXTURE0);
            RenderSystem.bindTexture(in.getColorTextureId());
            program.setInt(ScreenEffectDefinition.MAIN_SAMPLER, 0);
            for (int i = 0; i < this.aux.size(); i++) {
                RenderSystem.activeTexture(GL_TEXTURE0 + 1 + i);
                RenderSystem.bindTexture(this.auxTextures[i]);
                program.setInt(this.aux.get(i).input.sampler(), 1 + i);
            }

            // The effect's values win over the pass's, which win over the program's defaults.
            for (EffectShader.UniformDecl uniform : this.shader.uniforms.values()) {
                if (EffectShader.isBuiltin(uniform.name())) continue;
                EffectUniformImpl set = effect.uniformIfSet(uniform.name());
                float[] value = set != null ? set.values() : this.values.get(uniform.name());
                uniform.upload(program, value != null ? value : uniform.defaults());
            }
            Minecraft minecraft = Minecraft.getInstance();
            program.setMat4("ProjMat", owner.projection.setOrtho(0.0F, width, 0.0F, height, 0.1F, 1000.0F));
            program.setVec2("InSize", in.width, in.height);
            program.setVec2("OutSize", width, height);
            program.setVec2("ScreenSize", minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
            program.setFloat("Strength", effect.strength());
            program.setFloat("Time", info.time());
            program.setFloat("Age", effect.age());
            program.setFloat("Seed", info.seed());
            for (int i = 0; i < this.aux.size(); i++) program.setVec2("AuxSize" + i, auxSizes[i * 2], auxSizes[i * 2 + 1]);
            this.shader.blend.apply();

            // Only the color is cleared, unlike vanilla's passes: main's depth stays for later effects to read.
            out.bindWrite(false);
            RenderSystem.viewport(0, 0, width, height);
            RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
            RenderSystem.clear(GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
            RenderSystem.depthFunc(GL_ALWAYS);
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            builder.vertex(0.0, 0.0, 500.0).endVertex();
            builder.vertex(width, 0.0, 500.0).endVertex();
            builder.vertex(width, height, 500.0).endVertex();
            builder.vertex(0.0, height, 500.0).endVertex();
            BufferUploader.draw(builder.end());
            RenderSystem.depthFunc(GL_LEQUAL);
            out.unbindWrite();

            for (int i = this.aux.size(); i >= 0; i--) {
                RenderSystem.activeTexture(GL_TEXTURE0 + i);
                RenderSystem.bindTexture(0);
            }
            CanvasProgram.unuse();
            // In the reverse order they were changed, for a target read twice.
            for (int i = this.aux.size() - 1; i >= 0; i--) {
                if (this.auxFilters[i] >= 0) owner.target(this.aux.get(i).input.id(), main).setFilterMode(this.auxFilters[i]);
            }
            if (inFilter != wantedFilter) in.setFilterMode(inFilter);
        }
    }
}
