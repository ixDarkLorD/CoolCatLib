package net.ixdarklord.coolcatcanvas.internal.client.sky;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.ixdarklord.coolcatcanvas.api.client.sky.SkyBlend;
import net.ixdarklord.coolcatcanvas.internal.client.effect.ShaderProgram;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

// What every skybox shares: two static meshes (a cube around the camera, and a square for sprites), and shader
// programs keyed by what they depend on. Created on first use, on the render thread.
final class SkyResources {
    static final ResourceLocation VERTEX_SHADER = CoolCatCanvas.rl("sky/layer");
    private static final List<String> ATTRIBUTES = List.of("Position");

    private static @Nullable VertexBuffer cube;
    private static @Nullable VertexBuffer sprite;
    private static final Map<ProgramKey, ShaderProgram> PROGRAMS = new HashMap<>();

    private SkyResources() {}

    static VertexBuffer cube() {
        if (cube == null) cube = buildCube();
        return cube;
    }

    static VertexBuffer sprite() {
        if (sprite == null) sprite = buildSprite();
        return sprite;
    }

    /** Builds or reuses the program for a layer, compiling it. */
    static ShaderProgram program(ProgramKey key) throws IOException {
        ShaderProgram cached = PROGRAMS.get(key);
        if (cached != null) return cached;
        ShaderProgram program = ShaderProgram.compile(Minecraft.getInstance().getResourceManager(), VERTEX_SHADER, key.fragmentShader(),
                List.of("SKY_BLEND_" + key.blend().name()), ATTRIBUTES);
        PROGRAMS.put(key, program);
        CoolCatCanvas.LOGGER.debug("Compiled sky program {}", key);
        return program;
    }

    /** Frees compiled programs, whose shaders may have changed. */
    static void clearPrograms() {
        PROGRAMS.values().forEach(ShaderProgram::close);
        PROGRAMS.clear();
    }

    static void close() {
        clearPrograms();
        if (cube != null) {
            cube.close();
            cube = null;
        }
        if (sprite != null) {
            sprite.close();
            sprite = null;
        }
    }

    // Six faces of [-1, 1]^3; winding is irrelevant, culling is off while layers draw.
    private static VertexBuffer buildCube() {
        return upload(36, builder -> {
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
        });
    }

    // A sprite: the square [-1, 1] across x and z, facing down from y = 0.
    private static VertexBuffer buildSprite() {
        return upload(6, builder -> quad(builder, new float[][]{{-1.0F, 0.0F, -1.0F}, {1.0F, 0.0F, -1.0F}, {1.0F, 0.0F, 1.0F}, {-1.0F, 0.0F, 1.0F}}));
    }

    private static VertexBuffer upload(int vertices, Consumer<BufferBuilder> geometry) {
        VertexFormat format = DefaultVertexFormat.POSITION;
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(vertices * format.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.TRIANGLES, format);
            geometry.accept(builder);
            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            buffer.upload(builder.buildOrThrow());
            VertexBuffer.unbind();
            return buffer;
        }
    }

    private static void quad(BufferBuilder builder, float[][] corners) {
        for (int index : new int[]{0, 1, 2, 0, 2, 3}) builder.addVertex(corners[index][0], corners[index][1], corners[index][2]);
    }

    /** Everything a layer's program depends on, so layers alike share one. */
    record ProgramKey(ResourceLocation fragmentShader, SkyBlend blend) {}
}
