package net.ixdarklord.coolcatcanvas.api.client.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostChainConfig;
import net.minecraft.client.renderer.UniformValue;
import net.minecraft.resources.Identifier;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The passes and targets of a screen effect: the vanilla {@code post_effect} format, so vanilla's own effects load as
 * is. Loaded from {@code assets/<namespace>/post_effect/<path>.json}, or built with {@link #builder()}.
 * <p>
 * Vanilla parses every {@code post_effect} file too, so keep them valid for it (a {@code "vertex_shader"} on every
 * pass; {@code minecraft:core/screenquad} is the usual one). Differences from vanilla:
 * <ul>
 *     <li>Every uniform needs a {@code "name"} (vanilla's files all have one), by which the effect sets it.</li>
 *     <li>Every pass also gets an {@code EffectInfo} uniform block (see {@code coolcatcanvas:screen_effect.glsl}),
 *     and vanilla's {@code Globals} block.</li>
 *     <li>The only external target is {@code minecraft:main}; its depth can be read with
 *     {@code "use_depth_buffer": true}.</li>
 * </ul>
 */
public record ScreenEffectDefinition(Map<Identifier, PostChainConfig.InternalTarget> targets, List<Pass> passes) {
    public static final Identifier MAIN_TARGET = PostChain.MAIN_TARGET_ID;
    public static final Identifier SCREENQUAD_VERTEX_SHADER = Identifier.withDefaultNamespace("core/screenquad");
    /** Copies its {@code In} sampler, no uniforms. */
    public static final Identifier BLIT_FRAGMENT_SHADER = Identifier.withDefaultNamespace("core/blit_screen");
    /** Uniform block names taken by the runtime. */
    public static final Set<String> RESERVED_UNIFORM_BLOCKS = Set.of("SamplerInfo", "EffectInfo", "Globals", "Projection", "Fog", "Lighting");

    public static final Codec<ScreenEffectDefinition> CODEC = RecordCodecBuilder.<ScreenEffectDefinition>create(i -> i.group(
            Codec.unboundedMap(Identifier.CODEC, PostChainConfig.InternalTarget.CODEC).optionalFieldOf("targets", Map.of()).forGetter(ScreenEffectDefinition::targets),
            Pass.CODEC.listOf().fieldOf("passes").forGetter(ScreenEffectDefinition::passes)
    ).apply(i, ScreenEffectDefinition::new)).validate(ScreenEffectDefinition::validate);

    public ScreenEffectDefinition {
        targets = Map.copyOf(targets);
        passes = List.copyOf(passes);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * One fragment shader over the screen: reads {@code minecraft:main} as {@code In}, writes a swap target, then
     * copies it back. The simplest effect there is.
     *
     * @param uniformBlock the shader's uniform block name, ignored if {@code uniforms} is empty
     * @param uniforms     the block's uniforms, in declaration order
     */
    public static ScreenEffectDefinition simple(Identifier fragmentShader, String uniformBlock, UniformSpec... uniforms) {
        Identifier swap = Identifier.withDefaultNamespace("swap");
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

    private static DataResult<ScreenEffectDefinition> validate(ScreenEffectDefinition definition) {
        if (definition.passes.isEmpty()) return DataResult.error(() -> "A screen effect needs at least one pass");
        for (Pass pass : definition.passes) {
            Set<String> samplers = new HashSet<>();
            for (PostChainConfig.Input input : pass.inputs()) {
                if (!samplers.add(input.samplerName())) return DataResult.error(() -> "Repeated sampler name: " + input.samplerName());
            }
            for (Identifier target : pass.referencedTargets()) {
                if (!target.equals(MAIN_TARGET) && !definition.targets.containsKey(target)) {
                    return DataResult.error(() -> "Unknown target " + target + ": declare it under \"targets\" (the only external one is " + MAIN_TARGET + ")");
                }
            }
            for (Map.Entry<String, List<UniformSpec>> block : pass.uniforms().entrySet()) {
                if (RESERVED_UNIFORM_BLOCKS.contains(block.getKey())) return DataResult.error(() -> "Uniform block name is reserved: " + block.getKey());
                Set<String> names = new HashSet<>();
                for (UniformSpec spec : block.getValue()) {
                    if (!names.add(spec.name())) return DataResult.error(() -> "Repeated uniform " + spec.name() + " in block " + block.getKey());
                }
            }
        }
        return DataResult.success(definition);
    }

    /**
     * One draw of a fragment shader over its output target.
     *
     * @param uniforms uniform blocks by name, each listing its uniforms in the shader's declaration order
     */
    public record Pass(Identifier vertexShader, Identifier fragmentShader, List<PostChainConfig.Input> inputs, Identifier output, Map<String, List<UniformSpec>> uniforms) {
        public static final Codec<Pass> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("vertex_shader").forGetter(Pass::vertexShader),
                Identifier.CODEC.fieldOf("fragment_shader").forGetter(Pass::fragmentShader),
                PostChainConfig.Input.CODEC.listOf().optionalFieldOf("inputs", List.of()).forGetter(Pass::inputs),
                Identifier.CODEC.fieldOf("output").forGetter(Pass::output),
                Codec.unboundedMap(Codec.STRING, UniformSpec.CODEC.listOf()).optionalFieldOf("uniforms", Map.of()).forGetter(Pass::uniforms)
        ).apply(i, Pass::new));

        public Pass {
            inputs = List.copyOf(inputs);
            // Keeps the blocks' order stable, and each block's uniform order (which the shader's layout depends on).
            Map<String, List<UniformSpec>> copy = new LinkedHashMap<>();
            uniforms.forEach((name, specs) -> copy.put(name, List.copyOf(specs)));
            uniforms = Collections.unmodifiableMap(copy);
        }

        public Set<Identifier> referencedTargets() {
            Set<Identifier> targets = new HashSet<>();
            for (PostChainConfig.Input input : this.inputs) targets.addAll(input.referencedTargets());
            targets.add(this.output);
            return targets;
        }
    }

    /**
     * A named uniform and its default value: {@code {"name": "Amount", "type": "float", "value": 0.5}}.
     */
    public record UniformSpec(String name, UniformValue value) {
        public static final Codec<UniformSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(UniformSpec::name),
                UniformValue.Type.CODEC.<UniformValue>dispatchMap("type", UniformValue::type, UniformSpec::valueCodec).forGetter(UniformSpec::value)
        ).apply(i, UniformSpec::new));

        public static UniformSpec ofFloat(String name, float value) {
            return new UniformSpec(name, new UniformValue.FloatUniform(value));
        }

        public static UniformSpec ofInt(String name, int value) {
            return new UniformSpec(name, new UniformValue.IntUniform(value));
        }

        public static UniformSpec ofVec2(String name, float x, float y) {
            return new UniformSpec(name, new UniformValue.Vec2Uniform(new Vector2f(x, y)));
        }

        public static UniformSpec ofVec3(String name, float x, float y, float z) {
            return new UniformSpec(name, new UniformValue.Vec3Uniform(new Vector3f(x, y, z)));
        }

        public static UniformSpec ofVec4(String name, float x, float y, float z, float w) {
            return new UniformSpec(name, new UniformValue.Vec4Uniform(new Vector4f(x, y, z, w)));
        }

        private static MapCodec<? extends UniformValue> valueCodec(UniformValue.Type type) {
            Codec<? extends UniformValue> codec = switch (type) {
                case INT -> UniformValue.IntUniform.CODEC;
                case IVEC3 -> UniformValue.IVec3Uniform.CODEC;
                case FLOAT -> UniformValue.FloatUniform.CODEC;
                case VEC2 -> UniformValue.Vec2Uniform.CODEC;
                case VEC3 -> UniformValue.Vec3Uniform.CODEC;
                case VEC4 -> UniformValue.Vec4Uniform.CODEC;
                case MATRIX4X4 -> UniformValue.Matrix4x4Uniform.CODEC;
            };
            return codec.fieldOf("value");
        }
    }

    public static final class Builder {
        private final Map<Identifier, PostChainConfig.InternalTarget> targets = new LinkedHashMap<>();
        private final List<Pass> passes = new ArrayList<>();

        private Builder() {}

        /** A screen-sized target, cleared every frame. */
        public Builder target(Identifier id) {
            return this.target(id, new PostChainConfig.InternalTarget(Optional.empty(), Optional.empty(), false, 0));
        }

        /** A screen-sized target kept across frames, for effects that feed back into themselves (trails, echoes). */
        public Builder persistentTarget(Identifier id) {
            return this.target(id, new PostChainConfig.InternalTarget(Optional.empty(), Optional.empty(), true, 0));
        }

        public Builder target(Identifier id, PostChainConfig.InternalTarget target) {
            this.targets.put(id, target);
            return this;
        }

        public Builder pass(Identifier fragmentShader, Consumer<PassBuilder> configure) {
            PassBuilder pass = new PassBuilder(fragmentShader);
            configure.accept(pass);
            this.passes.add(pass.build());
            return this;
        }

        /** Copies one target into another. */
        public Builder blit(Identifier from, Identifier to) {
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
        private final Identifier fragmentShader;
        private Identifier vertexShader = SCREENQUAD_VERTEX_SHADER;
        private final List<PostChainConfig.Input> inputs = new ArrayList<>();
        private final Map<String, List<UniformSpec>> uniforms = new LinkedHashMap<>();
        private Identifier output = MAIN_TARGET;

        private PassBuilder(Identifier fragmentShader) {
            this.fragmentShader = fragmentShader;
        }

        public PassBuilder vertexShader(Identifier vertexShader) {
            this.vertexShader = vertexShader;
            return this;
        }

        /** Samples a target's color as {@code <sampler>Sampler}, with linear filtering. */
        public PassBuilder input(String sampler, Identifier target) {
            return this.input(new PostChainConfig.TargetInput(sampler, target, false, true));
        }

        /** Samples a target's depth as {@code <sampler>Sampler}. */
        public PassBuilder depthInput(String sampler, Identifier target) {
            return this.input(new PostChainConfig.TargetInput(sampler, target, true, false));
        }

        /** Samples {@code assets/<namespace>/textures/effect/<path>.png} as {@code <sampler>Sampler}. */
        public PassBuilder textureInput(String sampler, Identifier texture, int width, int height, boolean bilinear) {
            return this.input(new PostChainConfig.TextureInput(sampler, texture, width, height, bilinear));
        }

        public PassBuilder input(PostChainConfig.Input input) {
            this.inputs.add(input);
            return this;
        }

        /** Where it draws. Defaults to {@code minecraft:main}. */
        public PassBuilder output(Identifier target) {
            this.output = target;
            return this;
        }

        /** Adds uniforms to a block, in the shader's declaration order. */
        public PassBuilder uniforms(String block, UniformSpec... uniforms) {
            this.uniforms.computeIfAbsent(block, name -> new ArrayList<>()).addAll(List.of(uniforms));
            return this;
        }

        private Pass build() {
            return new Pass(this.vertexShader, this.fragmentShader, this.inputs, this.output, this.uniforms);
        }
    }

    private ScreenEffectDefinition validated() {
        return validate(this).getOrThrow(IllegalStateException::new);
    }
}
