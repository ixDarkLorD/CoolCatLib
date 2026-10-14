package net.ixdarklord.coolcatcanvas.internal.client.sky;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.ixdarklord.coolcatcanvas.api.client.sky.SkyBlend;
import net.ixdarklord.coolcatcanvas.internal.client.render.CanvasProgram;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

// What every skybox shares: two static meshes (a cube around the camera, and a square for sprites) and programs keyed
// by what they depend on. Created on first use, on the render thread.
final class SkyResources {
    static final ResourceLocation VERTEX_SHADER = CoolCatCanvas.rl("shaders/sky/layer.vsh");

    private static @Nullable VertexBuffer cube;
    private static @Nullable VertexBuffer sprite;
    private static final Map<ProgramKey, CanvasProgram> PROGRAMS = new HashMap<>();

    private SkyResources() {}

    static VertexBuffer cube() {
        if (cube == null) {
            // Six faces of [-1, 1]^3; winding is irrelevant, layers are drawn without culling.
            cube = upload(builder -> {
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
        return cube;
    }

    static VertexBuffer sprite() {
        // The square [-1, 1] across x and z, facing down from y = 0.
        if (sprite == null) sprite = upload(builder -> quad(builder, new float[][]{{-1.0F, 0.0F, -1.0F}, {1.0F, 0.0F, -1.0F}, {1.0F, 0.0F, 1.0F}, {-1.0F, 0.0F, 1.0F}}));
        return sprite;
    }

    /** Builds or reuses the program for a layer, compiling it. */
    static CanvasProgram program(ProgramKey key) throws IOException {
        CanvasProgram cached = PROGRAMS.get(key);
        if (cached != null) return cached;
        List<String> defines = key.textured()
                ? List.of("SKY_BLEND_" + key.blend().name(), "SKY_TEXTURED")
                : List.of("SKY_BLEND_" + key.blend().name());
        CanvasProgram program = CanvasProgram.compile(Minecraft.getInstance().getResourceManager(), "sky layer " + key.fragmentShader(),
                VERTEX_SHADER, key.fragmentFile(), defines);
        PROGRAMS.put(key, program);
        CoolCatCanvas.LOGGER.debug("Compiled sky program {}", key);
        return program;
    }

    /** Drops compiled programs, whose shaders may have changed. */
    static void clearPrograms() {
        PROGRAMS.values().forEach(CanvasProgram::close);
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

    private static VertexBuffer upload(Consumer<BufferBuilder> vertices) {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
        vertices.accept(builder);
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(builder.end());
        VertexBuffer.unbind();
        return buffer;
    }

    private static void quad(BufferBuilder builder, float[][] corners) {
        for (int index : new int[]{0, 1, 2, 0, 2, 3}) builder.vertex(corners[index][0], corners[index][1], corners[index][2]).endVertex();
    }

    /** Everything a layer's program depends on, so layers alike share one. */
    record ProgramKey(ResourceLocation fragmentShader, SkyBlend blend, boolean textured) {
        /** {@code mymod:sky/aurora} -> {@code mymod:shaders/sky/aurora.fsh} */
        ResourceLocation fragmentFile() {
            return new ResourceLocation(this.fragmentShader.getNamespace(), "shaders/" + this.fragmentShader.getPath() + ".fsh");
        }

        @Override
        public String toString() {
            return this.fragmentShader + " (" + this.blend.name().toLowerCase(Locale.ROOT) + (this.textured ? ", textured)" : ")");
        }
    }
}
