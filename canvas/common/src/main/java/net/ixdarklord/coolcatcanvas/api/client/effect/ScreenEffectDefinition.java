package net.ixdarklord.coolcatcanvas.api.client.effect;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The passes and targets of a screen effect, in the {@code post_effect} format of later Minecraft versions. Loaded from
 * {@code assets/<namespace>/post_effect/<path>.json}, or built with {@link #builder()}. A vanilla 1.21 post chain
 * ({@code assets/<namespace>/shaders/post/<path>.json}, vanilla's own included) loads too, when no {@code post_effect}
 * file has that id.
 * <p>
 * Each pass draws a fragment shader over its output target: {@code vertex_shader} and {@code fragment_shader} name
 * {@code assets/<namespace>/shaders/<path>.vsh} and {@code .fsh}, GLSL 150 with plain uniforms
 * ({@code minecraft:core/screenquad} and {@code minecraft:core/blit_screen} stand for {@link #SCREENQUAD_VERTEX_SHADER}
 * and {@link #BLIT_FRAGMENT_SHADER}).
 * <ul>
 *     <li>An input is sampled as {@code uniform sampler2D <sampler_name>Sampler}, its size in pixels read as
 *     {@code uniform vec2 <sampler_name>Size}. {@code InSize} is the first input's size, {@code OutSize} the output's,
 *     {@code ScreenSize} the window's, and {@code ProjMat} the projection {@link #SCREENQUAD_VERTEX_SHADER} uses.</li>
 *     <li>Every uniform needs a {@code "name"}, by which the effect sets it, and a type; the shader declares it as a
 *     plain uniform of that name. The block names they're grouped under only organize them.</li>
 *     <li>{@code Strength}, {@code Time}, {@code Age} and {@code Seed} are set for every pass; see
 *     {@code coolcatcanvas:screen_effect.glsl}.</li>
 *     <li>The only external target is {@code minecraft:main}; its depth can be read with
 *     {@code "use_depth_buffer": true}.</li>
 * </ul>
 */
public record ScreenEffectDefinition(Map<ResourceLocation, Target> targets, List<Pass> passes) {
    public static final ResourceLocation MAIN_TARGET = ResourceLocation.withDefaultNamespace("main");
    /** Draws the screen as a quad, giving the fragment shader {@code texCoord}. */
    public static final ResourceLocation SCREENQUAD_VERTEX_SHADER = CoolCatCanvas.rl("post/screenquad");
    /** Copies its {@code In} sampler, no uniforms. */
    public static final ResourceLocation BLIT_FRAGMENT_SHADER = CoolCatCanvas.rl("post/blit");
    /** Uniform block names taken by the runtime. */
    public static final Set<String> RESERVED_UNIFORM_BLOCKS = Set.of("SamplerInfo", "EffectInfo", "Globals", "Projection", "Fog", "Lighting");
    /** Uniform names the runtime sets, which no pass can declare. */
    public static final Set<String> RESERVED_UNIFORMS = Set.of("Strength", "Time", "Age", "Seed", "ProjMat", "InSize", "OutSize", "ScreenSize");

    public static final Codec<ScreenEffectDefinition> CODEC = RecordCodecBuilder.<ScreenEffectDefinition>create(i -> i.group(
            Codec.unboundedMap(ResourceLocation.CODEC, Target.CODEC).optionalFieldOf("targets", Map.of()).forGetter(ScreenEffectDefinition::targets),
            Pass.CODEC.listOf().fieldOf("passes").forGetter(ScreenEffectDefinition::passes)
    ).apply(i, ScreenEffectDefinition::new)).validate(ScreenEffectDefinition::validate);

    public ScreenEffectDefinition {
        targets = Collections.unmodifiableMap(new LinkedHashMap<>(targets));
        passes = List.copyOf(passes);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * One fragment shader over the screen: reads {@code minecraft:main} as {@code In}, writes a swap target, then
     * copies it back. The simplest effect there is.
     *
     * @param uniformBlock the name the uniforms are grouped under, ignored if {@code uniforms} is empty
     * @param uniforms     the shader's uniforms
     */
    public static ScreenEffectDefinition simple(ResourceLocation fragmentShader, String uniformBlock, UniformSpec... uniforms) {
        ResourceLocation swap = ResourceLocation.withDefaultNamespace("swap");
        return builder()
                .target(swap)
                .pass(fragmentShader, pass -> {
                    pass.input("In", MAIN_TARGET).output(swap);
                    if (uniforms.length > 0) pass.uniforms(uniformBlock, uniforms);
                })
                .blit(swap, MAIN_TARGET)
                .build();
    }

    /** Every uniform name any pass declares. */
    public Set<String> uniformNames() {
        Set<String> names = new HashSet<>();
        for (Pass pass : this.passes) {
            pass.uniforms().values().forEach(block -> block.forEach(spec -> names.add(spec.name())));
        }
        return names;
    }

    /** The shader ids of later versions' vanilla post shaders, mapped to CoolCatLib: Canvas's equivalents. */
    public static ResourceLocation resolveShader(ResourceLocation shader) {
        if (shader.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE)) {
            if (shader.getPath().equals("core/screenquad")) return SCREENQUAD_VERTEX_SHADER;
            if (shader.getPath().equals("core/blit_screen")) return BLIT_FRAGMENT_SHADER;
        }
        return shader;
    }

    /** Checks a definition built without the {@link Builder}, as loading it does. */
    public static DataResult<ScreenEffectDefinition> validate(ScreenEffectDefinition definition) {
        if (definition.passes.isEmpty()) return DataResult.error(() -> "A screen effect needs at least one pass");
        for (Pass pass : definition.passes) {
            Set<String> samplers = new HashSet<>();
            for (Input input : pass.inputs()) {
                if (!samplers.add(input.samplerName())) return DataResult.error(() -> "Repeated sampler name: " + input.samplerName());
            }
            for (ResourceLocation target : pass.referencedTargets()) {
                if (!target.equals(MAIN_TARGET) && !definition.targets.containsKey(target)) {
                    return DataResult.error(() -> "Unknown target " + target + ": declare it under \"targets\" (the only external one is " + MAIN_TARGET + ")");
                }
            }
            Set<String> names = new HashSet<>();
            for (Map.Entry<String, List<UniformSpec>> block : pass.uniforms().entrySet()) {
                if (RESERVED_UNIFORM_BLOCKS.contains(block.getKey())) return DataResult.error(() -> "Uniform block name is reserved: " + block.getKey());
                for (UniformSpec spec : block.getValue()) {
                    if (RESERVED_UNIFORMS.contains(spec.name())) return DataResult.error(() -> "Uniform " + spec.name() + " is set by the runtime");
                    if (!names.add(spec.name())) return DataResult.error(() -> "Repeated uniform " + spec.name() + " in a pass");
                }
            }
        }
        return DataResult.success(definition);
    }

    private ScreenEffectDefinition validated() {
        return validate(this).getOrThrow(IllegalStateException::new);
    }

    /**
     * A target a pass can draw into.
     *
     * @param width      in pixels, or the screen's
     * @param height     in pixels, or the screen's
     * @param persistent kept across frames, for effects that feed back into themselves; otherwise cleared to
     *                   {@code clearColor} every frame
     * @param clearColor packed ARGB
     */
    public record Target(Optional<Integer> width, Optional<Integer> height, boolean persistent, int clearColor) {
        public static final Codec<Target> CODEC = RecordCodecBuilder.create(i -> i.group(
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("width").forGetter(Target::width),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("height").forGetter(Target::height),
                Codec.BOOL.optionalFieldOf("persistent", false).forGetter(Target::persistent),
                ExtraCodecs.ARGB_COLOR_CODEC.optionalFieldOf("clear_color", 0).forGetter(Target::clearColor)
        ).apply(i, Target::new));

        /** Screen-sized, cleared every frame. */
        public static final Target SCREEN = new Target(Optional.empty(), Optional.empty(), false, 0);
        /** Screen-sized, kept across frames. */
        public static final Target PERSISTENT_SCREEN = new Target(Optional.empty(), Optional.empty(), true, 0);
    }

    /** What a pass samples: a target or a texture. */
    public sealed interface Input permits TargetInput, TextureInput {
        Codec<Input> CODEC = Codec.xor(TextureInput.CODEC, TargetInput.CODEC).xmap(
                either -> either.map(Function.identity(), Function.identity()),
                Input::toEither);

        String samplerName();

        Set<ResourceLocation> referencedTargets();

        boolean bilinear();

        private static Either<TextureInput, TargetInput> toEither(Input input) {
            return switch (input) {
                case TextureInput texture -> Either.left(texture);
                case TargetInput target -> Either.right(target);
            };
        }
    }

    /** Samples a target's color, or its depth with {@code useDepthBuffer}. */
    public record TargetInput(String samplerName, ResourceLocation targetId, boolean useDepthBuffer, boolean bilinear) implements Input {
        public static final Codec<TargetInput> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("sampler_name").forGetter(TargetInput::samplerName),
                ResourceLocation.CODEC.fieldOf("target").forGetter(TargetInput::targetId),
                Codec.BOOL.optionalFieldOf("use_depth_buffer", false).forGetter(TargetInput::useDepthBuffer),
                Codec.BOOL.optionalFieldOf("bilinear", false).forGetter(TargetInput::bilinear)
        ).apply(i, TargetInput::new));

        @Override
        public Set<ResourceLocation> referencedTargets() {
            return Set.of(this.targetId);
        }
    }

    /** Samples {@code assets/<namespace>/textures/effect/<path>.png}. */
    public record TextureInput(String samplerName, ResourceLocation location, int width, int height, boolean bilinear) implements Input {
        public static final Codec<TextureInput> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("sampler_name").forGetter(TextureInput::samplerName),
                ResourceLocation.CODEC.fieldOf("location").forGetter(TextureInput::location),
                ExtraCodecs.POSITIVE_INT.fieldOf("width").forGetter(TextureInput::width),
                ExtraCodecs.POSITIVE_INT.fieldOf("height").forGetter(TextureInput::height),
                Codec.BOOL.optionalFieldOf("bilinear", false).forGetter(TextureInput::bilinear)
        ).apply(i, TextureInput::new));

        @Override
        public Set<ResourceLocation> referencedTargets() {
            return Set.of();
        }
    }

    /**
     * One draw of a fragment shader over its output target.
     *
     * @param uniforms the pass's uniforms, grouped under names of the author's choosing
     */
    public record Pass(ResourceLocation vertexShader, ResourceLocation fragmentShader, List<Input> inputs, ResourceLocation output, Map<String, List<UniformSpec>> uniforms) {
        public static final Codec<Pass> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("vertex_shader").forGetter(Pass::vertexShader),
                ResourceLocation.CODEC.fieldOf("fragment_shader").forGetter(Pass::fragmentShader),
                Input.CODEC.listOf().optionalFieldOf("inputs", List.of()).forGetter(Pass::inputs),
                ResourceLocation.CODEC.fieldOf("output").forGetter(Pass::output),
                Codec.unboundedMap(Codec.STRING, UniformSpec.CODEC.listOf()).optionalFieldOf("uniforms", Map.of()).forGetter(Pass::uniforms)
        ).apply(i, Pass::new));

        public Pass {
            inputs = List.copyOf(inputs);
            // Keeps the groups' order stable, and each group's uniform order.
            Map<String, List<UniformSpec>> copy = new LinkedHashMap<>();
            uniforms.forEach((name, specs) -> copy.put(name, List.copyOf(specs)));
            uniforms = Collections.unmodifiableMap(copy);
        }

        public Set<ResourceLocation> referencedTargets() {
            Set<ResourceLocation> targets = new HashSet<>();
            for (Input input : this.inputs) targets.addAll(input.referencedTargets());
            targets.add(this.output);
            return targets;
        }
    }

    /** The GLSL types a uniform can have. */
    public enum UniformType implements StringRepresentable {
        INT("int", 1),
        IVEC3("ivec3", 3),
        FLOAT("float", 1),
        VEC2("vec2", 2),
        VEC3("vec3", 3),
        VEC4("vec4", 4),
        MATRIX4X4("matrix4x4", 16);

        public static final Codec<UniformType> CODEC = StringRepresentable.fromEnum(UniformType::values);

        private final String name;
        private final int components;

        UniformType(String name, int components) {
            this.name = name;
            this.components = components;
        }

        /** How many floats hold a value. */
        public int components() {
            return this.components;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        // In JSON: a number for int and float, a list for the rest (a matrix row by row).
        private MapCodec<UniformValue> valueCodec() {
            Codec<float[]> codec = switch (this) {
                case INT -> Codec.INT.xmap(v -> new float[]{v}, v -> (int) v[0]);
                case FLOAT -> Codec.FLOAT.xmap(v -> new float[]{v}, v -> v[0]);
                case IVEC3 -> Codec.INT.listOf(3, 3).xmap(v -> new float[]{v.get(0), v.get(1), v.get(2)}, v -> List.of((int) v[0], (int) v[1], (int) v[2]));
                case VEC2, VEC3, VEC4 -> Codec.FLOAT.listOf(this.components, this.components).xmap(UniformType::floats, UniformType::list);
                case MATRIX4X4 -> Codec.FLOAT.listOf(16, 16).xmap(v -> transpose(floats(v)), v -> list(transpose(v)));
            };
            return codec.xmap(values -> new UniformValue(this, values), UniformValue::components).fieldOf("value");
        }

        private static float[] floats(List<Float> values) {
            float[] array = new float[values.size()];
            for (int i = 0; i < array.length; i++) array[i] = values.get(i);
            return array;
        }

        private static List<Float> list(float[] values) {
            List<Float> list = new ArrayList<>(values.length);
            for (float value : values) list.add(value);
            return list;
        }

        private static float[] transpose(float[] m) {
            float[] out = new float[16];
            for (int row = 0; row < 4; row++) {
                for (int column = 0; column < 4; column++) out[column * 4 + row] = m[row * 4 + column];
            }
            return out;
        }
    }

    /**
     * A uniform's value.
     *
     * @param components the value's floats, {@link UniformType#components()} of them; a matrix column by column
     */
    public record UniformValue(UniformType type, float[] components) {
        public UniformValue {
            if (components.length != type.components()) {
                throw new IllegalArgumentException("A " + type.getSerializedName() + " takes " + type.components() + " values, got " + components.length);
            }
            components = components.clone();
        }

        @Override
        public float[] components() {
            return this.components.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof UniformValue value && value.type == this.type && Arrays.equals(value.components, this.components);
        }

        @Override
        public int hashCode() {
            return 31 * this.type.hashCode() + Arrays.hashCode(this.components);
        }

        @Override
        public String toString() {
            return this.type.getSerializedName() + Arrays.toString(this.components);
        }
    }

    /**
     * A named uniform and its default value: {@code {"name": "Amount", "type": "float", "value": 0.5}}.
     */
    public record UniformSpec(String name, UniformValue value) {
        public static final Codec<UniformSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(UniformSpec::name),
                UniformType.CODEC.dispatchMap("type", UniformValue::type, UniformType::valueCodec).forGetter(UniformSpec::value)
        ).apply(i, UniformSpec::new));

        public static UniformSpec ofFloat(String name, float value) {
            return new UniformSpec(name, new UniformValue(UniformType.FLOAT, new float[]{value}));
        }

        public static UniformSpec ofInt(String name, int value) {
            return new UniformSpec(name, new UniformValue(UniformType.INT, new float[]{value}));
        }

        public static UniformSpec ofVec2(String name, float x, float y) {
            return new UniformSpec(name, new UniformValue(UniformType.VEC2, new float[]{x, y}));
        }

        public static UniformSpec ofVec3(String name, float x, float y, float z) {
            return new UniformSpec(name, new UniformValue(UniformType.VEC3, new float[]{x, y, z}));
        }

        public static UniformSpec ofVec4(String name, float x, float y, float z, float w) {
            return new UniformSpec(name, new UniformValue(UniformType.VEC4, new float[]{x, y, z, w}));
        }

        public UniformType type() {
            return this.value.type();
        }
    }

    public static final class Builder {
        private final Map<ResourceLocation, Target> targets = new LinkedHashMap<>();
        private final List<Pass> passes = new ArrayList<>();

        private Builder() {}

        /** A screen-sized target, cleared every frame. */
        public Builder target(ResourceLocation id) {
            return this.target(id, Target.SCREEN);
        }

        /** A screen-sized target kept across frames, for effects that feed back into themselves (trails, echoes). */
        public Builder persistentTarget(ResourceLocation id) {
            return this.target(id, Target.PERSISTENT_SCREEN);
        }

        public Builder target(ResourceLocation id, Target target) {
            this.targets.put(id, target);
            return this;
        }

        public Builder pass(ResourceLocation fragmentShader, Consumer<PassBuilder> configure) {
            PassBuilder pass = new PassBuilder(fragmentShader);
            configure.accept(pass);
            this.passes.add(pass.build());
            return this;
        }

        /** Copies one target into another. */
        public Builder blit(ResourceLocation from, ResourceLocation to) {
            return this.pass(BLIT_FRAGMENT_SHADER, pass -> pass.input("In", from).output(to));
        }

        /**
         * @throws IllegalStateException if the definition is invalid
         */
        public ScreenEffectDefinition build() {
            return new ScreenEffectDefinition(this.targets, this.passes).validated();
        }
    }

    public static final class PassBuilder {
        private final ResourceLocation fragmentShader;
        private ResourceLocation vertexShader = SCREENQUAD_VERTEX_SHADER;
        private final List<Input> inputs = new ArrayList<>();
        private final Map<String, List<UniformSpec>> uniforms = new LinkedHashMap<>();
        private ResourceLocation output = MAIN_TARGET;

        private PassBuilder(ResourceLocation fragmentShader) {
            this.fragmentShader = fragmentShader;
        }

        public PassBuilder vertexShader(ResourceLocation vertexShader) {
            this.vertexShader = vertexShader;
            return this;
        }

        /** Samples a target's color as {@code <sampler>Sampler}, with linear filtering. */
        public PassBuilder input(String sampler, ResourceLocation target) {
            return this.input(new TargetInput(sampler, target, false, true));
        }

        /** Samples a target's depth as {@code <sampler>Sampler}. */
        public PassBuilder depthInput(String sampler, ResourceLocation target) {
            return this.input(new TargetInput(sampler, target, true, false));
        }

        /** Samples {@code assets/<namespace>/textures/effect/<path>.png} as {@code <sampler>Sampler}. */
        public PassBuilder textureInput(String sampler, ResourceLocation texture, int width, int height, boolean bilinear) {
            return this.input(new TextureInput(sampler, texture, width, height, bilinear));
        }

        public PassBuilder input(Input input) {
            this.inputs.add(input);
            return this;
        }

        /** Where it draws. Defaults to {@code minecraft:main}. */
        public PassBuilder output(ResourceLocation target) {
            this.output = target;
            return this;
        }

        /** Adds uniforms under a group name. */
        public PassBuilder uniforms(String block, UniformSpec... uniforms) {
            this.uniforms.computeIfAbsent(block, name -> new ArrayList<>()).addAll(List.of(uniforms));
            return this;
        }

        private Pass build() {
            return new Pass(this.vertexShader, this.fragmentShader, this.inputs, this.output, this.uniforms);
        }
    }
}
