package net.ixdarklord.coolcatcanvas.internal.client.effect;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.ixdarklord.coolcatcanvas.api.client.effect.ScreenEffectDefinition;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.FileUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.joml.Matrix4f;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL20;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A GL program linked from {@code assets/<namespace>/shaders/<path>.vsh} and {@code .fsh}, for screen effects and sky
 * layers. Vanilla's own shader classes only read the {@code minecraft} namespace and can't import includes into
 * effect programs, so these are compiled here: {@code #moj_import <namespace:file.glsl>} reads
 * {@code assets/<namespace>/shaders/include/file.glsl} ({@code minecraft} without a namespace), and
 * {@code #moj_import "file.glsl"} a file next to the shader.
 * <p>
 * Uniforms are plain GLSL 150 uniforms, set by name; setting one the program lacks does nothing. Only use it from the
 * render thread. Programs don't touch vanilla's cached shader state: vanilla rebinds its program for every draw.
 */
public final class ShaderProgram implements AutoCloseable {
    private static final int VERTEX = 35633;
    private static final int FRAGMENT = 35632;
    private static final int COMPILE_STATUS = 35713;
    private static final int LINK_STATUS = 35714;
    private static final int MAX_LOG = 32768;

    private final String name;
    private int id;
    private final Object2IntMap<String> locations = new Object2IntOpenHashMap<>();
    private final float[] matrix = new float[16];

    private ShaderProgram(String name, int id) {
        this.name = name;
        this.id = id;
        this.locations.defaultReturnValue(-2);
    }

    /**
     * Compiles and links a program.
     *
     * @param defines    {@code #define}s added after the {@code #version} line of both shaders
     * @param attributes vertex attribute names, bound to locations 0, 1, ... in order (as vanilla's vertex formats expect)
     * @throws IOException if a shader is missing or fails to compile or link, with the driver's log
     */
    public static ShaderProgram compile(ResourceProvider resources, ResourceLocation vertexShader, ResourceLocation fragmentShader, List<String> defines, List<String> attributes) throws IOException {
        RenderSystem.assertOnRenderThread();
        int vertex = compileShader(resources, VERTEX, vertexShader, ".vsh", defines);
        int fragment;
        try {
            fragment = compileShader(resources, FRAGMENT, fragmentShader, ".fsh", defines);
        } catch (IOException | RuntimeException e) {
            GlStateManager.glDeleteShader(vertex);
            throw e;
        }

        int program = GlStateManager.glCreateProgram();
        GlStateManager.glAttachShader(program, vertex);
        GlStateManager.glAttachShader(program, fragment);
        for (int i = 0; i < attributes.size(); i++) GlStateManager._glBindAttribLocation(program, i, attributes.get(i));
        GlStateManager.glLinkProgram(program);
        // Flagged for deletion: freed along with the program.
        GlStateManager.glDeleteShader(vertex);
        GlStateManager.glDeleteShader(fragment);
        String name = vertexShader + " / " + fragmentShader;
        if (GlStateManager.glGetProgrami(program, LINK_STATUS) == 0) {
            String log = StringUtils.trim(GlStateManager.glGetProgramInfoLog(program, MAX_LOG));
            GlStateManager.glDeleteProgram(program);
            throw new IOException("Couldn't link shaders " + name + ": " + log);
        }
        return new ShaderProgram(name, program);
    }

    private static int compileShader(ResourceProvider resources, int type, ResourceLocation id, String extension, List<String> defines) throws IOException {
        ResourceLocation file = id.withPath(path -> "shaders/" + path + extension);
        String source;
        try (Reader reader = resources.openAsReader(file)) {
            source = IOUtils.toString(reader);
        } catch (IOException e) {
            throw new IOException("Missing shader " + file, e);
        }

        List<String> parts = new ArrayList<>(new Preprocessor(resources, file).process(source));
        if (!defines.isEmpty() && !parts.isEmpty()) parts.set(0, withDefines(parts.getFirst(), defines));

        int shader = GlStateManager.glCreateShader(type);
        GlStateManager.glShaderSource(shader, parts);
        GlStateManager.glCompileShader(shader);
        if (GlStateManager.glGetShaderi(shader, COMPILE_STATUS) == 0) {
            String log = StringUtils.trim(GlStateManager.glGetShaderInfoLog(shader, MAX_LOG));
            GlStateManager.glDeleteShader(shader);
            throw new IOException("Couldn't compile shader " + file + ": " + log);
        }
        return shader;
    }

    // After the #version line, which GLSL wants first.
    private static String withDefines(String source, List<String> defines) {
        StringBuilder block = new StringBuilder();
        for (String define : defines) block.append("#define ").append(define).append('\n');
        int version = source.indexOf("#version");
        int lineEnd = version < 0 ? -1 : source.indexOf('\n', version);
        if (lineEnd < 0) return block + source;
        return source.substring(0, lineEnd + 1) + block + source.substring(lineEnd + 1);
    }

    public String name() {
        return this.name;
    }

    public void use() {
        GlStateManager._glUseProgram(this.id);
    }

    /** Unbinds any program, after drawing with these. */
    public static void release() {
        GlStateManager._glUseProgram(0);
    }

    /** The uniform's location, or -1 if the program has none by that name. */
    public int location(String uniform) {
        int location = this.locations.getInt(uniform);
        if (location == -2) {
            location = GlStateManager._glGetUniformLocation(this.id, uniform);
            this.locations.put(uniform, location);
        }
        return location;
    }

    public boolean has(String uniform) {
        return this.location(uniform) >= 0;
    }

    // ---- Uniforms, on the program in use ----

    public void setFloat(String uniform, float value) {
        int location = this.location(uniform);
        if (location >= 0) GL20.glUniform1f(location, value);
    }

    public void setVec2(String uniform, float x, float y) {
        int location = this.location(uniform);
        if (location >= 0) GL20.glUniform2f(location, x, y);
    }

    public void setVec4(String uniform, float x, float y, float z, float w) {
        int location = this.location(uniform);
        if (location >= 0) GL20.glUniform4f(location, x, y, z, w);
    }

    /** Sets a {@code vec4[]} from its components, 4 per element. */
    public void setVec4Array(String uniform, float[] values) {
        int location = this.location(uniform);
        if (location >= 0) GL20.glUniform4fv(location, values);
    }

    public void setInt(String uniform, int value) {
        int location = this.location(uniform);
        if (location >= 0) GL20.glUniform1i(location, value);
    }

    public void setMatrix(String uniform, Matrix4f value) {
        int location = this.location(uniform);
        if (location >= 0) GL20.glUniformMatrix4fv(location, false, value.get(this.matrix));
    }

    /** Points a sampler at a texture unit. */
    public void setSampler(String uniform, int unit) {
        this.setInt(uniform, unit);
    }

    /**
     * Sets a uniform of a definition's type from its components (a matrix column-major); missing components are 0,
     * floats are truncated for int types.
     */
    public void set(String uniform, ScreenEffectDefinition.UniformType type, float[] v) {
        int location = this.location(uniform);
        if (location < 0) return;
        switch (type) {
            case FLOAT -> GL20.glUniform1f(location, at(v, 0));
            case VEC2 -> GL20.glUniform2f(location, at(v, 0), at(v, 1));
            case VEC3 -> GL20.glUniform3f(location, at(v, 0), at(v, 1), at(v, 2));
            case VEC4 -> GL20.glUniform4f(location, at(v, 0), at(v, 1), at(v, 2), at(v, 3));
            case INT -> GL20.glUniform1i(location, (int) at(v, 0));
            case IVEC3 -> GL20.glUniform3i(location, (int) at(v, 0), (int) at(v, 1), (int) at(v, 2));
            case MATRIX4X4 -> {
                for (int i = 0; i < 16; i++) this.matrix[i] = at(v, i);
                GL20.glUniformMatrix4fv(location, false, this.matrix);
            }
        }
    }

    private static float at(float[] values, int index) {
        return index < values.length ? values[index] : 0.0F;
    }

    @Override
    public void close() {
        if (this.id > 0) {
            RenderSystem.assertOnRenderThread();
            GlStateManager.glDeleteProgram(this.id);
            this.id = 0;
        }
    }

    // Resolves #moj_import against resources, each file once per shader.
    private static final class Preprocessor extends GlslPreprocessor {
        private final ResourceProvider resources;
        private final ResourceLocation file;
        private final Set<ResourceLocation> imported = new HashSet<>();

        private Preprocessor(ResourceProvider resources, ResourceLocation file) {
            this.resources = resources;
            this.file = file;
        }

        @Override
        public @Nullable String applyImport(boolean relative, String path) {
            ResourceLocation location;
            if (relative) {
                String folder = this.file.getPath().substring(0, this.file.getPath().lastIndexOf('/') + 1);
                location = ResourceLocation.tryBuild(this.file.getNamespace(), FileUtil.normalizeResourcePath(folder + path));
            } else {
                ResourceLocation id = ResourceLocation.tryParse(path);
                location = id == null ? null : id.withPath(include -> FileUtil.normalizeResourcePath("shaders/include/" + include));
            }
            if (location == null) return "#error Invalid import " + path + "\n";
            if (!this.imported.add(location)) return null;
            try (Reader reader = this.resources.openAsReader(location)) {
                return IOUtils.toString(reader);
            } catch (IOException e) {
                CoolCatCanvas.LOGGER.error("Could not open GLSL import {} from {}: {}", location, this.file, e.getMessage());
                return "#error Missing import " + location + "\n";
            }
        }
    }
}
