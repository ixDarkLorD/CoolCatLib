package net.ixdarklord.coolcatcanvas.api.client.effect;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.ResourceLocationException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

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
 * The passes and targets of a screen effect: vanilla's post chain format, so vanilla's own effects (and any made for
 * them) load as they are. Loaded from {@code assets/<namespace>/shaders/post/<path>.json}, or built with
 * {@link #builder()}.
 * <p>
 * Each pass runs a program, {@code assets/<namespace>/shaders/program/<path>.json} in vanilla's format: its
 * {@code vertex} and {@code fragment} shaders (namespaced, or vanilla's), its {@code blend}, and its {@code uniforms}
 * with their types and defaults. Differences from vanilla:
 * <ul>
 *     <li>Programs, shaders and textures can be namespaced ({@code "name": "mymod:swirl"}), and shaders can use
 *     {@code #moj_import}, e.g. {@code #moj_import <coolcatcanvas:screen_effect.glsl>}.</li>
 *     <li>Every pass also gets {@code Strength}, {@code Time}, {@code Age} and {@code Seed} (see
 *     {@code coolcatcanvas:screen_effect.glsl}), and vanilla's {@code ProjMat}, {@code InSize}, {@code OutSize},
 *     {@code ScreenSize} and {@code AuxSize<i>}, whether the program's JSON lists them or not.</li>
 *     <li>A pass may say {@code "bilinear": true} to sample its {@code intarget} with linear filtering, and an
 *     {@code auxtargets} entry may too for a target.</li>
 *     <li>The only external target is {@code minecraft:main}; its depth can be read with
 *     {@code "id": "minecraft:main:depth"}, as vanilla allows. Every target keeps its content from one frame to the
 *     next until a pass draws into it, as in vanilla's post chains.</li>
 * </ul>
 */
public record ScreenEffectDefinition(Map<ResourceLocation, Target> targets, List<Pass> passes) {
    public static final ResourceLocation MAIN_TARGET = new ResourceLocation("main");
    /** The sampler a pass reads its {@code intarget} as. */
    public static final String MAIN_SAMPLER = "DiffuseSampler";
    /**
     * Copies its input as it is, alpha included. Vanilla's {@code blit} blends by the input's alpha instead, which
     * darkens a copy of {@code minecraft:main}.
     */
    public static final ResourceLocation BLIT_PROGRAM = CoolCatCanvas.rl("blit");
    /** Uniforms the runtime writes into every pass that has them (besides {@code AuxSize<i>}), whatever else sets them. */
    public static final Set<String> BUILTIN_UNIFORMS = Set.of("ProjMat", "InSize", "OutSize", "ScreenSize", "Time", "Strength", "Age", "Seed");
    public static final int MAX_UNIFORM_VALUES = 16;

    private static final Codec<Map<ResourceLocation, Target>> TARGETS_CODEC = NamedTarget.CODEC.listOf().comapFlatMap(list -> {
        Map<ResourceLocation, Target> targets = new LinkedHashMap<>();
        for (NamedTarget target : list) {
            if (targets.put(target.id(), target.target()) != null) return DataResult.error(() -> "Target " + target.id() + " is already defined");
        }
        return DataResult.success(targets);
    }, targets -> targets.entrySet().stream().map(entry -> new NamedTarget(entry.getKey(), entry.getValue())).toList());

    public static final Codec<ScreenEffectDefinition> CODEC = ExtraCodecs.validate(RecordCodecBuilder.<ScreenEffectDefinition>create(i -> i.group(
            TARGETS_CODEC.optionalFieldOf("targets", Map.of()).forGetter(ScreenEffectDefinition::targets),
            Pass.CODEC.listOf().fieldOf("passes").forGetter(ScreenEffectDefinition::passes)
    ).apply(i, ScreenEffectDefinition::new)), ScreenEffectDefinition::validate);

    public ScreenEffectDefinition {
        targets = Collections.unmodifiableMap(new LinkedHashMap<>(targets));
        passes = List.copyOf(passes);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * One program over the screen: reads {@code minecraft:main} as {@code DiffuseSampler}, writes a swap target, then
     * copies it back. The simplest effect there is.
     *
     * @param program  {@code assets/<namespace>/shaders/program/<path>.json}
     * @param uniforms values for the program's uniforms, overriding its defaults
     */
    public static ScreenEffectDefinition simple(ResourceLocation program, UniformSpec... uniforms) {
        ResourceLocation swap = new ResourceLocation("swap");
        return builder()
                .target(swap)
                .pass(program, pass -> pass.input(MAIN_TARGET).output(swap).uniforms(uniforms))
                .blit(swap, MAIN_TARGET)
                .build();
    }

    /** Every uniform name a pass gives a value to. The programs declare more, with their defaults. */
    public Set<String> uniformNames() {
        Set<String> names = new HashSet<>();
        for (Pass pass : this.passes) pass.uniforms().forEach(spec -> names.add(spec.name()));
        return names;
    }

    /** Whether {@code id} names one of the definition's targets or {@code minecraft:main}. */
    public boolean isTarget(ResourceLocation id) {
        return id.equals(MAIN_TARGET) || this.targets.containsKey(id);
    }

    private static DataResult<ScreenEffectDefinition> validate(ScreenEffectDefinition definition) {
        if (definition.passes.isEmpty()) return DataResult.error(() -> "A screen effect needs at least one pass");
        for (Map.Entry<ResourceLocation, Target> target : definition.targets.entrySet()) {
            if (target.getKey().equals(MAIN_TARGET)) return DataResult.error(() -> MAIN_TARGET + " is external and can't be declared");
        }
        for (int i = 0; i < definition.passes.size(); i++) {
            Pass pass = definition.passes.get(i);
            int index = i;
            for (ResourceLocation target : List.of(pass.input(), pass.output())) {
                if (!definition.isTarget(target)) {
                    return DataResult.error(() -> "Pass " + index + ": unknown target " + target + ": declare it under \"targets\" (the only external one is " + MAIN_TARGET + ")");
                }
            }
            if (pass.input().equals(pass.output())) {
                return DataResult.error(() -> "Pass " + index + " reads the target it writes, " + pass.output() + "; draw into another target and copy it back");
            }
            Set<String> samplers = new HashSet<>();
            samplers.add(MAIN_SAMPLER);
            for (AuxTarget aux : pass.auxTargets()) {
                if (!samplers.add(aux.sampler())) return DataResult.error(() -> "Pass " + index + ": repeated sampler name " + aux.sampler());
                boolean target = definition.isTarget(aux.id());
                if (aux.depth() && !target) return DataResult.error(() -> "Pass " + index + ": " + aux.id() + " isn't a target, so its depth can't be read");
                if (target && !aux.depth() && aux.id().equals(pass.output())) {
                    return DataResult.error(() -> "Pass " + index + " reads the target it writes, " + pass.output() + "; draw into another target and copy it back");
                }
            }
            Set<String> names = new HashSet<>();
            for (UniformSpec spec : pass.uniforms()) {
                if (!names.add(spec.name())) return DataResult.error(() -> "Pass " + index + ": repeated uniform " + spec.name());
            }
        }
        return DataResult.success(definition);
    }

    /**
     * A target's size; screen-sized where not given. Screen-sized targets follow the window.
     */
    public record Target(Optional<Integer> width, Optional<Integer> height) {
        public static final Target SCREEN_SIZED = new Target(Optional.empty(), Optional.empty());

        public static Target sized(int width, int height) {
            return new Target(Optional.of(width), Optional.of(height));
        }
    }

    // A "targets" entry: a name alone, or {"name", "width", "height"}.
    private record NamedTarget(ResourceLocation id, Target target) {
        private static final Codec<NamedTarget> OBJECT_CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("name").forGetter(NamedTarget::id),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("width").forGetter(target -> target.target().width()),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("height").forGetter(target -> target.target().height())
        ).apply(i, (id, width, height) -> new NamedTarget(id, new Target(width, height))));
        static final Codec<NamedTarget> CODEC = Codec.either(ResourceLocation.CODEC, OBJECT_CODEC).xmap(
                either -> either.map(id -> new NamedTarget(id, Target.SCREEN_SIZED), target -> target),
                target -> target.target().equals(Target.SCREEN_SIZED) ? Either.left(target.id()) : Either.right(target));
    }

    /**
     * One draw of a program over its output target.
     *
     * @param program    {@code assets/<namespace>/shaders/program/<path>.json} ({@code "name"})
     * @param input      the target read as {@code DiffuseSampler} ({@code "intarget"})
     * @param output     the target drawn into ({@code "outtarget"})
     * @param auxTargets more targets and textures to read ({@code "auxtargets"})
     * @param uniforms   values for the program's uniforms, overriding its defaults
     * @param bilinear   whether the input is sampled with linear filtering
     */
    public record Pass(ResourceLocation program, ResourceLocation input, ResourceLocation output, List<AuxTarget> auxTargets, List<UniformSpec> uniforms, boolean bilinear) {
        public static final Codec<Pass> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("name").forGetter(Pass::program),
                ResourceLocation.CODEC.fieldOf("intarget").forGetter(Pass::input),
                ResourceLocation.CODEC.fieldOf("outtarget").forGetter(Pass::output),
                AuxTarget.CODEC.listOf().optionalFieldOf("auxtargets", List.of()).forGetter(Pass::auxTargets),
                UniformSpec.CODEC.listOf().optionalFieldOf("uniforms", List.of()).forGetter(Pass::uniforms),
                Codec.BOOL.optionalFieldOf("bilinear", false).forGetter(Pass::bilinear)
        ).apply(i, Pass::new));

        public Pass {
            auxTargets = List.copyOf(auxTargets);
            uniforms = List.copyOf(uniforms);
        }

        public Set<ResourceLocation> referencedTargets(ScreenEffectDefinition definition) {
            Set<ResourceLocation> targets = new HashSet<>();
            targets.add(this.input);
            targets.add(this.output);
            for (AuxTarget aux : this.auxTargets) {
                if (definition.isTarget(aux.id())) targets.add(aux.id());
            }
            return targets;
        }
    }

    /**
     * Another input of a pass, sampled as {@code sampler}: a target (its color, or its depth), or else a texture,
     * {@code assets/<namespace>/textures/effect/<path>.png}. In JSON, {@code {"name": "PrevSampler", "id": "previous"}},
     * with {@code "id": "<target>:depth"} for a target's depth.
     *
     * @param width    a texture's width, for its {@code AuxSize<i>}; 0 to read it from the texture
     * @param height   a texture's height, likewise
     * @param bilinear linear filtering
     */
    public record AuxTarget(String sampler, ResourceLocation id, boolean depth, int width, int height, boolean bilinear) {
        private static final String DEPTH_SUFFIX = ":depth";
        public static final Codec<AuxTarget> CODEC = Raw.CODEC.comapFlatMap(Raw::parse, Raw::of);

        public static AuxTarget target(String sampler, ResourceLocation target) {
            return new AuxTarget(sampler, target, false, 0, 0, false);
        }

        public static AuxTarget depth(String sampler, ResourceLocation target) {
            return new AuxTarget(sampler, target, true, 0, 0, false);
        }

        public static AuxTarget texture(String sampler, ResourceLocation texture, int width, int height, boolean bilinear) {
            return new AuxTarget(sampler, texture, false, width, height, bilinear);
        }

        // The JSON form, whose id may end with ":depth".
        private record Raw(String name, String id, int width, int height, boolean bilinear) {
            static final Codec<Raw> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.STRING.fieldOf("name").forGetter(Raw::name),
                    Codec.STRING.fieldOf("id").forGetter(Raw::id),
                    ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("width", 0).forGetter(Raw::width),
                    ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("height", 0).forGetter(Raw::height),
                    Codec.BOOL.optionalFieldOf("bilinear", false).forGetter(Raw::bilinear)
            ).apply(i, Raw::new));

            static Raw of(AuxTarget aux) {
                return new Raw(aux.sampler, aux.id + (aux.depth ? DEPTH_SUFFIX : ""), aux.width, aux.height, aux.bilinear);
            }

            DataResult<AuxTarget> parse() {
                boolean depth = this.id.endsWith(DEPTH_SUFFIX);
                String id = depth ? this.id.substring(0, this.id.length() - DEPTH_SUFFIX.length()) : this.id;
                try {
                    return DataResult.success(new AuxTarget(this.name, new ResourceLocation(id), depth, this.width, this.height, this.bilinear));
                } catch (ResourceLocationException e) {
                    String message = e.getMessage();
                    return DataResult.error(() -> "Invalid aux target id " + this.id + ": " + message);
                }
            }
        }

    }

    /**
     * A value for a program's uniform: {@code {"name": "Amount", "values": [0.5]}}. The program declares its type;
     * values are converted to it (truncated for ints; missing components are 0).
     */
    public record UniformSpec(String name, List<Float> values) {
        public static final Codec<UniformSpec> CODEC = ExtraCodecs.validate(RecordCodecBuilder.<UniformSpec>create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(UniformSpec::name),
                Codec.FLOAT.listOf().fieldOf("values").forGetter(UniformSpec::values)
        ).apply(i, UniformSpec::new)), spec -> spec.values.isEmpty() || spec.values.size() > MAX_UNIFORM_VALUES
                ? DataResult.error(() -> "Uniform " + spec.name + " needs 1 to " + MAX_UNIFORM_VALUES + " values")
                : DataResult.success(spec));

        public UniformSpec {
            values = List.copyOf(values);
        }

        public static UniformSpec of(String name, float... values) {
            List<Float> list = new ArrayList<>(values.length);
            for (float value : values) list.add(value);
            return new UniformSpec(name, list);
        }

        public static UniformSpec ofFloat(String name, float value) {
            return of(name, value);
        }

        public static UniformSpec ofInt(String name, int value) {
            return of(name, value);
        }

        public static UniformSpec ofVec2(String name, float x, float y) {
            return of(name, x, y);
        }

        public static UniformSpec ofVec3(String name, float x, float y, float z) {
            return of(name, x, y, z);
        }

        public static UniformSpec ofVec4(String name, float x, float y, float z, float w) {
            return of(name, x, y, z, w);
        }

        public float[] toArray() {
            float[] array = new float[this.values.size()];
            for (int i = 0; i < array.length; i++) array[i] = this.values.get(i);
            return array;
        }
    }

    public static final class Builder {
        private final Map<ResourceLocation, Target> targets = new LinkedHashMap<>();
        private final List<Pass> passes = new ArrayList<>();

        private Builder() {}

        /** A screen-sized target. */
        public Builder target(ResourceLocation id) {
            return this.target(id, Target.SCREEN_SIZED);
        }

        /**
         * A screen-sized target for effects that feed back into themselves (trails, echoes). On 1.20.1 every target
         * keeps its content across frames, as in vanilla's post chains, so this is the same as {@link #target}.
         */
        public Builder persistentTarget(ResourceLocation id) {
            return this.target(id);
        }

        public Builder target(ResourceLocation id, Target target) {
            this.targets.put(id, target);
            return this;
        }

        /** @param program {@code assets/<namespace>/shaders/program/<path>.json} */
        public Builder pass(ResourceLocation program, Consumer<PassBuilder> configure) {
            PassBuilder pass = new PassBuilder(program);
            configure.accept(pass);
            this.passes.add(pass.build());
            return this;
        }

        /** Copies one target into another, alpha included. */
        public Builder blit(ResourceLocation from, ResourceLocation to) {
            return this.pass(BLIT_PROGRAM, pass -> pass.input(from).output(to));
        }

        /**
         * @throws IllegalStateException if the definition is invalid
         */
        public ScreenEffectDefinition build() {
            DataResult<ScreenEffectDefinition> result = validate(new ScreenEffectDefinition(this.targets, this.passes));
            Optional<DataResult.PartialResult<ScreenEffectDefinition>> error = result.error();
            if (error.isPresent()) throw new IllegalStateException(error.get().message());
            return result.result().orElseThrow();
        }
    }

    public static final class PassBuilder {
        private final ResourceLocation program;
        private ResourceLocation input = MAIN_TARGET;
        private ResourceLocation output = MAIN_TARGET;
        private boolean bilinear;
        private final List<AuxTarget> auxTargets = new ArrayList<>();
        private final List<UniformSpec> uniforms = new ArrayList<>();

        private PassBuilder(ResourceLocation program) {
            this.program = program;
        }

        /** The target read as {@code DiffuseSampler}. Defaults to {@code minecraft:main}. */
        public PassBuilder input(ResourceLocation target) {
            this.input = target;
            return this;
        }

        /** Samples the input with linear filtering. Off by default. */
        public PassBuilder bilinear(boolean bilinear) {
            this.bilinear = bilinear;
            return this;
        }

        /** Samples another target's color as {@code sampler}, its full name in the shader (e.g. {@code PrevSampler}). */
        public PassBuilder input(String sampler, ResourceLocation target) {
            return this.input(AuxTarget.target(sampler, target));
        }

        /** Samples a target's depth as {@code sampler}. */
        public PassBuilder depthInput(String sampler, ResourceLocation target) {
            return this.input(AuxTarget.depth(sampler, target));
        }

        /** Samples {@code assets/<namespace>/textures/effect/<path>.png} as {@code sampler}. */
        public PassBuilder textureInput(String sampler, ResourceLocation texture, int width, int height, boolean bilinear) {
            return this.input(AuxTarget.texture(sampler, texture, width, height, bilinear));
        }

        public PassBuilder input(AuxTarget input) {
            this.auxTargets.add(input);
            return this;
        }

        /** Where it draws. Defaults to {@code minecraft:main}. */
        public PassBuilder output(ResourceLocation target) {
            this.output = target;
            return this;
        }

        /** Values for the program's uniforms, overriding its defaults. */
        public PassBuilder uniforms(UniformSpec... uniforms) {
            this.uniforms.addAll(List.of(uniforms));
            return this;
        }

        public PassBuilder uniform(String name, float... values) {
            return this.uniforms(UniformSpec.of(name, values));
        }

        private Pass build() {
            return new Pass(this.program, this.input, this.output, this.auxTargets, this.uniforms, this.bilinear);
        }
    }
}
