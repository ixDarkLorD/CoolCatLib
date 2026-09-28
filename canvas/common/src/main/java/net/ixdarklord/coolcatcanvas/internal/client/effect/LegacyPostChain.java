package net.ixdarklord.coolcatcanvas.internal.client.effect;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.ixdarklord.coolcatcanvas.api.client.effect.ScreenEffectDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Reads a vanilla 1.21 post chain (shaders/post/<path>.json, its programs in shaders/program/) as a screen effect
// definition, so vanilla's own effects register as they are. Its targets keep their contents across frames, as vanilla's
// do; the programs' uniforms become the effect's, with the pass's values as defaults.
final class LegacyPostChain {
    // Set by the runtime, or by vanilla's PostPass for its own purposes.
    private static final Set<String> RUNTIME_UNIFORMS = Set.of("ProjMat", "InSize", "OutSize", "ScreenSize", "Time", "Strength", "Age", "Seed");
    private static final String GROUP = "Program";

    private LegacyPostChain() {}

    static ScreenEffectDefinition read(ResourceProvider resources, ResourceLocation location, JsonObject json) throws IOException {
        Map<ResourceLocation, ScreenEffectDefinition.Target> targets = new LinkedHashMap<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "targets", new JsonArray())) {
            String name;
            Optional<Integer> width = Optional.empty();
            Optional<Integer> height = Optional.empty();
            if (GsonHelper.isStringValue(element)) {
                name = element.getAsString();
            } else {
                JsonObject target = GsonHelper.convertToJsonObject(element, "target");
                name = GsonHelper.getAsString(target, "name");
                if (target.has("width")) width = Optional.of(GsonHelper.getAsInt(target, "width"));
                if (target.has("height")) height = Optional.of(GsonHelper.getAsInt(target, "height"));
            }
            targets.put(target(name), new ScreenEffectDefinition.Target(width, height, true, 0));
        }

        Map<ResourceLocation, Program> programs = new HashMap<>();
        List<ScreenEffectDefinition.Pass> passes = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "passes", new JsonArray())) {
            JsonObject pass = GsonHelper.convertToJsonObject(element, "pass");
            ResourceLocation programId = ResourceLocation.parse(GsonHelper.getAsString(pass, "name"));
            Program program = programs.get(programId);
            if (program == null) {
                program = Program.read(resources, programId);
                programs.put(programId, program);
            }
            boolean linear = GsonHelper.getAsBoolean(pass, "use_linear_filter", false);

            List<ScreenEffectDefinition.Input> inputs = new ArrayList<>();
            inputs.add(new ScreenEffectDefinition.TargetInput("Diffuse", target(GsonHelper.getAsString(pass, "intarget")), false, linear));
            for (JsonElement auxElement : GsonHelper.getAsJsonArray(pass, "auxtargets", new JsonArray())) {
                JsonObject aux = GsonHelper.convertToJsonObject(auxElement, "auxtarget");
                String sampler = GsonHelper.getAsString(aux, "name");
                if (sampler.endsWith("Sampler")) sampler = sampler.substring(0, sampler.length() - "Sampler".length());
                String id = GsonHelper.getAsString(aux, "id");
                if (id.endsWith(":depth")) {
                    inputs.add(new ScreenEffectDefinition.TargetInput(sampler, target(id.substring(0, id.lastIndexOf(':'))), true, false));
                } else {
                    ResourceLocation target = target(id);
                    if (target.equals(ScreenEffectDefinition.MAIN_TARGET) || targets.containsKey(target)) {
                        inputs.add(new ScreenEffectDefinition.TargetInput(sampler, target, false, linear));
                    } else {
                        inputs.add(new ScreenEffectDefinition.TextureInput(sampler, target, GsonHelper.getAsInt(aux, "width"), GsonHelper.getAsInt(aux, "height"), GsonHelper.getAsBoolean(aux, "bilinear", false)));
                    }
                }
            }

            Map<String, float[]> overrides = new HashMap<>();
            for (JsonElement uniformElement : GsonHelper.getAsJsonArray(pass, "uniforms", new JsonArray())) {
                JsonObject uniform = GsonHelper.convertToJsonObject(uniformElement, "uniform");
                overrides.put(GsonHelper.getAsString(uniform, "name"), floats(GsonHelper.getAsJsonArray(uniform, "values")));
            }
            List<ScreenEffectDefinition.UniformSpec> uniforms = new ArrayList<>();
            for (ScreenEffectDefinition.UniformSpec spec : program.uniforms) {
                float[] override = overrides.get(spec.name());
                if (override == null) {
                    uniforms.add(spec);
                } else {
                    float[] values = spec.value().components();
                    System.arraycopy(override, 0, values, 0, Math.min(override.length, values.length));
                    uniforms.add(new ScreenEffectDefinition.UniformSpec(spec.name(), new ScreenEffectDefinition.UniformValue(spec.type(), values)));
                }
            }

            passes.add(new ScreenEffectDefinition.Pass(program.vertexShader, program.fragmentShader, inputs, target(GsonHelper.getAsString(pass, "outtarget")),
                    uniforms.isEmpty() ? Map.of() : Map.of(GROUP, uniforms)));
        }

        return ScreenEffectDefinition.validate(new ScreenEffectDefinition(targets, passes))
                .getOrThrow(message -> new IOException("Invalid post chain " + location + ": " + message));
    }

    // Vanilla names targets with bare words ("swap"): they read as minecraft ids, like minecraft:main.
    private static ResourceLocation target(String name) throws IOException {
        ResourceLocation id = ResourceLocation.tryParse(name);
        if (id == null) throw new IOException("Invalid target name " + name);
        return id;
    }

    private static float[] floats(JsonArray array) {
        float[] values = new float[array.size()];
        for (int i = 0; i < values.length; i++) values[i] = GsonHelper.convertToFloat(array.get(i), "value");
        return values;
    }

    // shaders/program/<path>.json: its shaders, and its uniforms with their defaults.
    private record Program(ResourceLocation vertexShader, ResourceLocation fragmentShader, List<ScreenEffectDefinition.UniformSpec> uniforms) {
        static Program read(ResourceProvider resources, ResourceLocation id) throws IOException {
            ResourceLocation location = id.withPath(path -> "shaders/program/" + path + ".json");
            JsonObject json;
            try (Reader reader = resources.openAsReader(location)) {
                json = GsonHelper.convertToJsonObject(JsonParser.parseReader(reader), "program");
            } catch (IOException e) {
                throw new IOException("Missing post chain program " + location, e);
            }
            ResourceLocation vertex = shader(id, GsonHelper.getAsString(json, "vertex"));
            ResourceLocation fragment = shader(id, GsonHelper.getAsString(json, "fragment"));
            List<ScreenEffectDefinition.UniformSpec> uniforms = new ArrayList<>();
            for (JsonElement element : GsonHelper.getAsJsonArray(json, "uniforms", new JsonArray())) {
                JsonObject uniform = GsonHelper.convertToJsonObject(element, "uniform");
                String name = GsonHelper.getAsString(uniform, "name");
                if (RUNTIME_UNIFORMS.contains(name) || name.startsWith("AuxSize")) continue;
                ScreenEffectDefinition.UniformType type = type(GsonHelper.getAsString(uniform, "type"), GsonHelper.getAsInt(uniform, "count", 1));
                if (type == null) continue;
                float[] values = new float[type.components()];
                float[] given = floats(GsonHelper.getAsJsonArray(uniform, "values", new JsonArray()));
                // Like vanilla, a single value fills every component.
                for (int i = 0; i < values.length; i++) values[i] = given.length == 1 ? given[0] : i < given.length ? given[i] : 0.0F;
                uniforms.add(new ScreenEffectDefinition.UniformSpec(name, new ScreenEffectDefinition.UniformValue(type, values)));
            }
            return new Program(vertex, fragment, uniforms);
        }

        // Vanilla's program shaders live in shaders/program/, named without it.
        private static ResourceLocation shader(ResourceLocation program, String name) throws IOException {
            ResourceLocation id = name.indexOf(':') >= 0 ? ResourceLocation.tryParse(name) : ResourceLocation.tryBuild(program.getNamespace(), name);
            if (id == null) throw new IOException("Invalid shader name " + name + " in program " + program);
            return id.withPrefix("program/");
        }

        private static ScreenEffectDefinition.@Nullable UniformType type(String type, int count) {
            return switch (type) {
                case "float" -> switch (count) {
                    case 1 -> ScreenEffectDefinition.UniformType.FLOAT;
                    case 2 -> ScreenEffectDefinition.UniformType.VEC2;
                    case 3 -> ScreenEffectDefinition.UniformType.VEC3;
                    case 4 -> ScreenEffectDefinition.UniformType.VEC4;
                    default -> null;
                };
                case "int" -> count == 1 ? ScreenEffectDefinition.UniformType.INT : count == 3 ? ScreenEffectDefinition.UniformType.IVEC3 : null;
                case "matrix4x4" -> ScreenEffectDefinition.UniformType.MATRIX4X4;
                default -> null;
            };
        }
    }
}
