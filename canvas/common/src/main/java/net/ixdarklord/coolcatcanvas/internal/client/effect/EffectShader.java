package net.ixdarklord.coolcatcanvas.internal.client.effect;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.shaders.BlendMode;
import com.mojang.blaze3d.systems.RenderSystem;
import net.ixdarklord.coolcatcanvas.api.client.effect.ScreenEffectDefinition;
import net.ixdarklord.coolcatcanvas.internal.client.render.CanvasProgram;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL20;

import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

// A post program, assets/<namespace>/shaders/program/<path>.json in vanilla's format: its shaders, blend and declared
// uniforms. Shared by every pass and effect using it, until the next resource reload.
final class EffectShader implements AutoCloseable {
    private static final int GL_FUNC_ADD = 32774;
    private static final int GL_ONE = 1;
    private static final int GL_ZERO = 0;

    final ResourceLocation id;
    final CanvasProgram program;
    final Blend blend;
    /** Declared uniforms the program uses, in declaration order. */
    final Map<String, UniformDecl> uniforms;

    private EffectShader(ResourceLocation id, CanvasProgram program, Blend blend, Map<String, UniformDecl> uniforms) {
        this.id = id;
        this.program = program;
        this.blend = blend;
        this.uniforms = uniforms;
    }

    static EffectShader load(ResourceManager resources, ResourceLocation id) throws IOException {
        ResourceLocation location = new ResourceLocation(id.getNamespace(), "shaders/program/" + id.getPath() + ".json");
        Optional<Resource> resource = resources.getResource(location);
        if (resource.isEmpty()) throw new IOException("Missing screen effect program " + location);

        JsonObject json;
        try (Reader reader = resource.get().openAsReader()) {
            json = GsonHelper.convertToJsonObject(JsonParser.parseReader(reader), "program");
        } catch (RuntimeException e) {
            throw new IOException("Invalid screen effect program " + location + ": " + e.getMessage(), e);
        }

        ResourceLocation vertex;
        ResourceLocation fragment;
        Blend blend;
        Map<String, UniformDecl> declared = new LinkedHashMap<>();
        try {
            vertex = shaderFile(GsonHelper.getAsString(json, "vertex"), ".vsh");
            fragment = shaderFile(GsonHelper.getAsString(json, "fragment"), ".fsh");
            blend = Blend.parse(GsonHelper.getAsJsonObject(json, "blend", null));
            JsonArray uniforms = GsonHelper.getAsJsonArray(json, "uniforms", null);
            if (uniforms != null) {
                for (JsonElement element : uniforms) {
                    UniformDecl uniform = UniformDecl.parse(GsonHelper.convertToJsonObject(element, "uniform"));
                    declared.put(uniform.name(), uniform);
                }
            }
        } catch (RuntimeException e) {
            throw new IOException("Invalid screen effect program " + location + ": " + e.getMessage(), e);
        }

        CanvasProgram program = CanvasProgram.compile(resources, id.toString(), vertex, fragment, List.of());
        // Drops what the linker optimized away, as vanilla does.
        declared.values().removeIf(uniform -> !program.has(uniform.name()));
        return new EffectShader(id, program, blend, Collections.unmodifiableMap(declared));
    }

    // "sobel" -> minecraft:shaders/program/sobel.vsh; "mymod:swirl" -> mymod:shaders/program/swirl.fsh
    private static ResourceLocation shaderFile(String name, String extension) {
        ResourceLocation id = new ResourceLocation(name);
        return new ResourceLocation(id.getNamespace(), "shaders/program/" + id.getPath() + extension);
    }

    @Override
    public void close() {
        this.program.close();
    }

    /** A uniform the program's JSON declares, with its type and default value. */
    record UniformDecl(String name, Kind kind, int count, float[] defaults) {
        static UniformDecl parse(JsonObject json) {
            String name = GsonHelper.getAsString(json, "name");
            String type = GsonHelper.getAsString(json, "type").toLowerCase(Locale.ROOT);
            Kind kind = switch (type) {
                case "int" -> Kind.INT;
                case "float" -> Kind.FLOAT;
                case "matrix2x2" -> Kind.MATRIX2;
                case "matrix3x3" -> Kind.MATRIX3;
                case "matrix4x4" -> Kind.MATRIX4;
                default -> throw new IllegalArgumentException("Uniform " + name + " has an unknown type: " + type);
            };
            int count = GsonHelper.getAsInt(json, "count", kind.matrixSize > 0 ? kind.matrixSize : 1);
            if (kind.matrixSize > 0 ? count != kind.matrixSize : count < 1 || count > 4) {
                throw new IllegalArgumentException("Uniform " + name + " can't have a count of " + count + " as " + type);
            }
            JsonArray values = GsonHelper.getAsJsonArray(json, "values", new JsonArray());
            if (values.size() > 1 && values.size() != count) {
                throw new IllegalArgumentException("Uniform " + name + " has " + values.size() + " values, expected " + count);
            }
            float[] defaults = new float[count];
            for (int i = 0; i < count; i++) {
                // A single value fills every component, as in vanilla.
                if (!values.isEmpty()) defaults[i] = GsonHelper.convertToFloat(values.get(values.size() == 1 ? 0 : i), "value");
            }
            return new UniformDecl(name, kind, count, defaults);
        }

        /** Uploads the first {@code count} components of {@code values} (missing ones are 0) to the program in use. */
        void upload(CanvasProgram program, float @Nullable [] values) {
            int location = program.location(this.name);
            if (location == -1) return;
            float[] v = values != null && values.length >= this.count ? values : pad(values, this.count);
            switch (this.kind) {
                case INT -> {
                    switch (this.count) {
                        case 1 -> GL20.glUniform1i(location, (int) v[0]);
                        case 2 -> GL20.glUniform2i(location, (int) v[0], (int) v[1]);
                        case 3 -> GL20.glUniform3i(location, (int) v[0], (int) v[1], (int) v[2]);
                        default -> GL20.glUniform4i(location, (int) v[0], (int) v[1], (int) v[2], (int) v[3]);
                    }
                }
                case FLOAT -> {
                    switch (this.count) {
                        case 1 -> GL20.glUniform1f(location, v[0]);
                        case 2 -> GL20.glUniform2f(location, v[0], v[1]);
                        case 3 -> GL20.glUniform3f(location, v[0], v[1], v[2]);
                        default -> GL20.glUniform4f(location, v[0], v[1], v[2], v[3]);
                    }
                }
                case MATRIX2 -> GL20.glUniformMatrix2fv(location, false, v.length == 4 ? v : Arrays.copyOf(v, 4));
                case MATRIX3 -> GL20.glUniformMatrix3fv(location, false, v.length == 9 ? v : Arrays.copyOf(v, 9));
                case MATRIX4 -> GL20.glUniformMatrix4fv(location, false, v.length == 16 ? v : Arrays.copyOf(v, 16));
            }
        }

        private static float[] pad(float @Nullable [] values, int count) {
            float[] padded = new float[count];
            if (values != null) System.arraycopy(values, 0, padded, 0, Math.min(values.length, count));
            return padded;
        }

        enum Kind {
            INT(0),
            FLOAT(0),
            MATRIX2(4),
            MATRIX3(9),
            MATRIX4(16);

            final int matrixSize;

            Kind(int matrixSize) {
                this.matrixSize = matrixSize;
            }
        }
    }

    /** A program's {@code blend}, as vanilla parses it; applied in full on every pass. */
    record Blend(boolean opaque, boolean separate, int function, int sourceColor, int destColor, int sourceAlpha, int destAlpha) {
        static final Blend OPAQUE = new Blend(true, false, GL_FUNC_ADD, GL_ONE, GL_ZERO, GL_ONE, GL_ZERO);

        static Blend parse(@Nullable JsonObject json) {
            if (json == null) return OPAQUE;
            int function = GL_FUNC_ADD;
            int sourceColor = GL_ONE;
            int destColor = GL_ZERO;
            int sourceAlpha = GL_ONE;
            int destAlpha = GL_ZERO;
            boolean separate = false;
            if (GsonHelper.isStringValue(json, "func")) function = BlendMode.stringToBlendFunc(json.get("func").getAsString());
            if (GsonHelper.isStringValue(json, "srcrgb")) sourceColor = BlendMode.stringToBlendFactor(json.get("srcrgb").getAsString());
            if (GsonHelper.isStringValue(json, "dstrgb")) destColor = BlendMode.stringToBlendFactor(json.get("dstrgb").getAsString());
            if (GsonHelper.isStringValue(json, "srcalpha")) {
                sourceAlpha = BlendMode.stringToBlendFactor(json.get("srcalpha").getAsString());
                separate = true;
            }
            if (GsonHelper.isStringValue(json, "dstalpha")) {
                destAlpha = BlendMode.stringToBlendFactor(json.get("dstalpha").getAsString());
                separate = true;
            }
            boolean opaque = function == GL_FUNC_ADD && sourceColor == GL_ONE && destColor == GL_ZERO && sourceAlpha == GL_ONE && destAlpha == GL_ZERO;
            return new Blend(opaque, separate, function, sourceColor, destColor, sourceAlpha, destAlpha);
        }

        void apply() {
            if (this.opaque) {
                RenderSystem.disableBlend();
                return;
            }
            RenderSystem.enableBlend();
            RenderSystem.blendEquation(this.function);
            if (this.separate) RenderSystem.blendFuncSeparate(this.sourceColor, this.destColor, this.sourceAlpha, this.destAlpha);
            else RenderSystem.blendFunc(this.sourceColor, this.destColor);
        }
    }

    /** Whether the runtime writes this uniform itself. */
    static boolean isBuiltin(String name) {
        return ScreenEffectDefinition.BUILTIN_UNIFORMS.contains(name) || name.startsWith("AuxSize");
    }
}
