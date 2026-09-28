package net.ixdarklord.coolcatcanvas.internal.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.systems.RenderSystem;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.FileUtil;
import net.minecraft.ResourceLocationException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.Reader;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A GLSL program linked from a vertex and a fragment shader in resources. Sources go through vanilla's preprocessor,
 * with namespaced imports: {@code #moj_import <namespace:file.glsl>} reads {@code assets/<namespace>/shaders/include/},
 * {@code #moj_import "file.glsl"} a path next to the importing shader. Extra {@code #define}s go right after
 * {@code #version}. The {@code Position} attribute is bound to location 0, where 1.20.1's vertex formats put it.
 * <p>
 * Kept out of vanilla's program caches, so its names never collide with vanilla's and reloading vanilla's shaders
 * never frees it. Render thread only.
 */
public final class CanvasProgram implements AutoCloseable {
    private static final Pattern VERSION_LINE = Pattern.compile("#version[^\\n]*\\n");
    private static final int MAX_LOG_LENGTH = 32768;

    private final String name;
    private int id;
    private final Map<String, Integer> locations = new HashMap<>();

    private CanvasProgram(String name, int id) {
        this.name = name;
        this.id = id;
    }

    /**
     * @param vertex   the vertex shader's full path, e.g. {@code minecraft:shaders/program/sobel.vsh}
     * @param fragment the fragment shader's full path
     * @param defines  names to {@code #define} in both shaders
     * @throws IOException if a file is missing, or a shader doesn't compile or link
     */
    public static CanvasProgram compile(ResourceManager resources, String name, ResourceLocation vertex, ResourceLocation fragment, List<String> defines) throws IOException {
        RenderSystem.assertOnRenderThread();
        int vertexShader = compileShader(resources, GL20.GL_VERTEX_SHADER, vertex, defines);
        int fragmentShader;
        try {
            fragmentShader = compileShader(resources, GL20.GL_FRAGMENT_SHADER, fragment, defines);
        } catch (IOException | RuntimeException e) {
            GlStateManager.glDeleteShader(vertexShader);
            throw e;
        }

        int program = GlStateManager.glCreateProgram();
        if (program <= 0) {
            GlStateManager.glDeleteShader(vertexShader);
            GlStateManager.glDeleteShader(fragmentShader);
            throw new IOException("Could not create a shader program for " + name);
        }
        GlStateManager.glAttachShader(program, vertexShader);
        GlStateManager.glAttachShader(program, fragmentShader);
        GlStateManager._glBindAttribLocation(program, 0, "Position");
        GlStateManager.glLinkProgram(program);
        // Attached shaders are only flagged, and go with the program.
        GlStateManager.glDeleteShader(vertexShader);
        GlStateManager.glDeleteShader(fragmentShader);
        if (GlStateManager.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
            String log = GlStateManager.glGetProgramInfoLog(program, MAX_LOG_LENGTH).trim();
            GlStateManager.glDeleteProgram(program);
            throw new IOException("Couldn't link " + vertex + " with " + fragment + ": " + log);
        }
        return new CanvasProgram(name, program);
    }

    private static int compileShader(ResourceManager resources, int type, ResourceLocation file, List<String> defines) throws IOException {
        List<String> source = new Preprocessor(resources, file).process(read(resources, file));
        if (!defines.isEmpty()) source.set(0, withDefines(source.get(0), defines));
        int shader = GlStateManager.glCreateShader(type);
        GlStateManager.glShaderSource(shader, source);
        GlStateManager.glCompileShader(shader);
        if (GlStateManager.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GlStateManager.glGetShaderInfoLog(shader, MAX_LOG_LENGTH).trim();
            GlStateManager.glDeleteShader(shader);
            throw new IOException("Couldn't compile " + file + ": " + log);
        }
        return shader;
    }

    private static String withDefines(String source, List<String> defines) {
        StringBuilder lines = new StringBuilder();
        for (String define : defines) lines.append("#define ").append(define).append('\n');
        Matcher version = VERSION_LINE.matcher(source);
        return version.find() ? source.substring(0, version.end()) + lines + source.substring(version.end()) : lines + source;
    }

    static String read(ResourceManager resources, ResourceLocation file) throws IOException {
        Optional<Resource> resource = resources.getResource(file);
        if (resource.isEmpty()) throw new IOException("Missing shader file " + file);
        try (Reader reader = resource.get().openAsReader()) {
            return IOUtils.toString(reader);
        }
    }

    public String name() {
        return this.name;
    }

    public int id() {
        return this.id;
    }

    public void use() {
        GlStateManager._glUseProgram(this.id);
    }

    public static void unuse() {
        GlStateManager._glUseProgram(0);
    }

    /** The uniform's location, or -1 if the program doesn't use it. */
    public int location(String uniform) {
        Integer location = this.locations.get(uniform);
        if (location == null) {
            location = GlStateManager._glGetUniformLocation(this.id, uniform);
            this.locations.put(uniform, location);
        }
        return location;
    }

    public boolean has(String uniform) {
        return this.location(uniform) != -1;
    }

    // ---- Uniforms, for the program in use; names it doesn't use are skipped ----

    public void setInt(String uniform, int value) {
        int location = this.location(uniform);
        if (location != -1) GL20.glUniform1i(location, value);
    }

    public void setFloat(String uniform, float value) {
        int location = this.location(uniform);
        if (location != -1) GL20.glUniform1f(location, value);
    }

    public void setVec2(String uniform, float x, float y) {
        int location = this.location(uniform);
        if (location != -1) GL20.glUniform2f(location, x, y);
    }

    public void setVec4(String uniform, float x, float y, float z, float w) {
        int location = this.location(uniform);
        if (location != -1) GL20.glUniform4f(location, x, y, z, w);
    }

    public void setVec4(String uniform, float[] xyzw) {
        this.setVec4(uniform, xyzw[0], xyzw[1], xyzw[2], xyzw[3]);
    }

    /** A {@code vec4[]}, from 4 floats per element. */
    public void setVec4Array(String uniform, float[] values) {
        int location = this.location(uniform);
        if (location != -1) GL20.glUniform4fv(location, values);
    }

    public void setMat4(String uniform, Matrix4fc matrix) {
        int location = this.location(uniform);
        if (location == -1) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(16);
            matrix.get(buffer);
            GL20.glUniformMatrix4fv(location, false, buffer);
        }
    }

    @Override
    public void close() {
        if (this.id > 0) {
            RenderSystem.assertOnRenderThread();
            GlStateManager.glDeleteProgram(this.id);
            this.id = 0;
        }
    }

    // Vanilla's preprocessor, resolving imports with their namespace.
    private static final class Preprocessor extends GlslPreprocessor {
        private final ResourceManager resources;
        private final ResourceLocation file;
        private final Set<ResourceLocation> imported = new HashSet<>();

        private Preprocessor(ResourceManager resources, ResourceLocation file) {
            this.resources = resources;
            this.file = file;
        }

        @Override
        public @Nullable String applyImport(boolean relative, String path) {
            ResourceLocation location;
            try {
                if (relative) {
                    String directory = FileUtil.getFullResourcePath(this.file.getPath());
                    location = new ResourceLocation(this.file.getNamespace(), FileUtil.normalizeResourcePath(directory + path));
                } else {
                    ResourceLocation include = new ResourceLocation(path);
                    location = new ResourceLocation(include.getNamespace(), "shaders/include/" + include.getPath());
                }
            } catch (ResourceLocationException e) {
                return "#error Invalid import " + path;
            }
            if (!this.imported.add(location)) return null;
            try {
                return read(this.resources, location);
            } catch (IOException e) {
                CoolCatCanvas.LOGGER.error("Could not open GLSL import {}: {}", location, e.getMessage());
                return "#error " + e.getMessage();
            }
        }
    }
}
