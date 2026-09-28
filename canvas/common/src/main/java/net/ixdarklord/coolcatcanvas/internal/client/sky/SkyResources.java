package net.ixdarklord.coolcatcanvas.internal.client.sky;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.ixdarklord.coolcatcanvas.api.client.sky.SkyBlend;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

// What every skybox shares: one static mesh (a cube around the camera, then a square for sprites), one ring of uniform
// blocks, and pipelines keyed by what they depend on. Created on first use, on the render thread.
final class SkyResources {
    static final Identifier VERTEX_SHADER = CoolCatCanvas.rl("sky/layer");
    static final int CUBE_FIRST_VERTEX = 0;
    static final int CUBE_VERTICES = 36;
    static final int SPRITE_FIRST_VERTEX = CUBE_VERTICES;
    static final int SPRITE_VERTICES = 6;

    private static @Nullable GpuBuffer mesh;
    private static @Nullable DynamicUniformStorage<LayerUniform> uniforms;
    private static final Map<PipelineKey, RenderPipeline> PIPELINES = new HashMap<>();
    private static int nextPipeline;

    private SkyResources() {}

    static GpuBuffer mesh() {
        if (mesh == null) mesh = buildMesh();
        return mesh;
    }

    static DynamicUniformStorage<LayerUniform> uniforms() {
        if (uniforms == null) uniforms = new DynamicUniformStorage<>("CoolCatLib: Canvas sky layers UBO", LayerUniform.SIZE, 8);
        return uniforms;
    }

    static void endFrame() {
        if (uniforms != null) uniforms.endFrame();
    }

    /** Builds or reuses the pipeline for a layer, compiling it. */
    static RenderPipeline pipeline(PipelineKey key) {
        RenderPipeline cached = PIPELINES.get(key);
        if (cached != null) return cached;
        RenderPipeline pipeline = key.build(CoolCatCanvas.rl("pipeline/sky_layer_" + nextPipeline++));
        CompiledRenderPipeline compiled = RenderSystem.getDevice().precompilePipeline(pipeline);
        if (!compiled.isValid()) {
            throw new IllegalStateException("Sky shader " + key.fragmentShader() + " failed to compile or link (see the log above)");
        }
        PIPELINES.put(key, pipeline);
        CoolCatCanvas.LOGGER.debug("Compiled sky pipeline {}", key);
        return pipeline;
    }

    /** Drops compiled pipelines, whose shaders may have changed. */
    static void clearPipelines() {
        PIPELINES.clear();
    }

    static void close() {
        PIPELINES.clear();
        if (mesh != null) {
            mesh.close();
            mesh = null;
        }
        if (uniforms != null) {
            uniforms.close();
            uniforms = null;
        }
    }

    private static GpuBuffer buildMesh() {
        VertexFormat format = DefaultVertexFormat.POSITION;
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized((CUBE_VERTICES + SPRITE_VERTICES) * format.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.TRIANGLES, format);
            // Six faces of [-1, 1]^3; winding is irrelevant, the pipelines don't cull.
            for (int axis = 0; axis < 3; axis++) {
                for (int side = -1; side <= 1; side += 2) {
                    float[][] corners = new float[4][3];
                    for (int corner = 0; corner < 4; corner++) {
                        float u = (corner == 1 || corner == 2) ? 1.0F : -1.0F;
                        float v = corner >= 2 ? 1.0F : -1.0F;
                        corners[corner][axis] = side;
                        corners[corner][(axis + 1) % 3] = u;
                        corners[corner][(axis + 2) % 3] = v;
                    }
                    quad(builder, corners);
                }
            }
            // A sprite: the square [-1, 1] across x and z, facing down from y = 0.
            quad(builder, new float[][]{{-1.0F, 0.0F, -1.0F}, {1.0F, 0.0F, -1.0F}, {1.0F, 0.0F, 1.0F}, {-1.0F, 0.0F, 1.0F}});
            try (MeshData data = builder.buildOrThrow()) {
                return RenderSystem.getDevice().createBuffer(() -> "CoolCatLib: Canvas sky mesh", GpuBuffer.USAGE_VERTEX, data.vertexBuffer());
            }
        }
    }

    private static void quad(BufferBuilder builder, float[][] corners) {
        for (int index : new int[]{0, 1, 2, 0, 2, 3}) builder.addVertex(corners[index][0], corners[index][1], corners[index][2]);
    }

    /** Everything a layer's pipeline depends on, so layers alike share one. */
    record PipelineKey(Identifier fragmentShader, SkyBlend blend, boolean textured) {
        RenderPipeline build(Identifier location) {
            RenderPipeline.Builder builder = RenderPipeline.builder()
                    .withLocation(location)
                    .withVertexShader(VERTEX_SHADER)
                    .withFragmentShader(this.fragmentShader)
                    .withShaderDefine("SKY_BLEND_" + this.blend.name())
                    .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                    .withUniform("Fog", UniformType.UNIFORM_BUFFER)
                    .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                    .withUniform(LayerUniform.BLOCK, UniformType.UNIFORM_BUFFER)
                    .withColorTargetState(new ColorTargetState(this.blend.function()))
                    .withCull(false)
                    .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.TRIANGLES);
            if (this.textured) builder.withSampler("Sampler0");
            return builder.build();
        }
    }
}
