package net.ixdarklord.coolcatcanvas.api.client.sky;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The layers of a skybox and what it hides of vanilla's sky. Loaded from {@code assets/<namespace>/skybox/<path>.json},
 * or built with {@link #builder()}:
 * <pre>{@code
 * {
 *   "dimensions": ["minecraft:overworld"],
 *   "hide": ["sky", "sunrise", "stars"],
 *   "priority": 0,
 *   "fade_in": 20,
 *   "fade_out": 20,
 *   "layers": [
 *     {"type": "cubemap", "texture": "mymod:textures/sky/day.png", "day_fade": {"fade_in": [22500, 0], "fade_out": [12000, 13500]}},
 *     {"type": "panorama", "texture": "mymod:textures/sky/night.png", "day_fade": {"fade_in": [12000, 13500], "fade_out": [22500, 0]}}
 *   ]
 * }
 * }</pre>
 * A resource pack can add a skybox with no code at all: every file in a {@code skybox} folder that lists
 * {@code dimensions} is shown in those dimensions.
 *
 * @param layers     drawn in order, each in its {@linkplain SkyLayerDefinition#stage() stage}; may be empty, to only
 *                   hide parts of vanilla's sky
 * @param hide       the parts of vanilla's sky hidden while the skybox shows
 * @param dimensions where the skybox shows by itself; empty to leave it to code (or the server) to enable
 * @param priority   skyboxes are drawn by ascending priority, so higher ones end up on top
 * @param fadeIn     ticks it takes to fade in
 * @param fadeOut    ticks it takes to fade out
 */
public record SkyboxDefinition(
        List<SkyLayerDefinition> layers,
        Set<VanillaSky> hide,
        List<ResourceKey<Level>> dimensions,
        int priority,
        int fadeIn,
        int fadeOut
) {
    public static final int DEFAULT_FADE = 20;

    public static final Codec<SkyboxDefinition> CODEC = ExtraCodecs.validate(RecordCodecBuilder.<SkyboxDefinition>create(i -> i.group(
            SkyLayerDefinition.CODEC.listOf().optionalFieldOf("layers", List.of()).forGetter(SkyboxDefinition::layers),
            VanillaSky.CODEC.listOf().<Set<VanillaSky>>xmap(Set::copyOf, List::copyOf).optionalFieldOf("hide", Set.of()).forGetter(SkyboxDefinition::hide),
            ResourceKey.codec(Registries.DIMENSION).listOf().optionalFieldOf("dimensions", List.of()).forGetter(SkyboxDefinition::dimensions),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(SkyboxDefinition::priority),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("fade_in", DEFAULT_FADE).forGetter(SkyboxDefinition::fadeIn),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("fade_out", DEFAULT_FADE).forGetter(SkyboxDefinition::fadeOut)
    ).apply(i, SkyboxDefinition::new)), SkyboxDefinition::validate);

    public SkyboxDefinition {
        List<SkyLayerDefinition> named = new ArrayList<>(layers.size());
        for (int i = 0; i < layers.size(); i++) {
            SkyLayerDefinition layer = layers.get(i);
            named.add(layer.name().isEmpty() ? layer.withName("layer_" + i) : layer);
        }
        layers = List.copyOf(named);
        hide = Set.copyOf(hide);
        dimensions = List.copyOf(dimensions);
    }

    public static Builder builder() {
        return new Builder();
    }

    private static DataResult<SkyboxDefinition> validate(SkyboxDefinition definition) {
        Set<String> names = new HashSet<>();
        for (SkyLayerDefinition layer : definition.layers) {
            if (!names.add(layer.name())) return DataResult.error(() -> "Repeated sky layer name: " + layer.name());
        }
        return DataResult.success(definition);
    }

    public static final class Builder {
        private final List<SkyLayerDefinition> layers = new ArrayList<>();
        private final Set<VanillaSky> hide = EnumSet.noneOf(VanillaSky.class);
        private final List<ResourceKey<Level>> dimensions = new ArrayList<>();
        private int priority;
        private int fadeIn = DEFAULT_FADE;
        private int fadeOut = DEFAULT_FADE;

        private Builder() {}

        public Builder layer(SkyLayerDefinition layer) {
            this.layers.add(layer);
            return this;
        }

        public Builder layer(SkyLayerDefinition.Builder layer) {
            return this.layer(layer.build());
        }

        public Builder hide(VanillaSky... parts) {
            this.hide.addAll(List.of(parts));
            return this;
        }

        /** Hides the whole vanilla sky, End included: for a skybox that draws all of it. */
        public Builder hideAll() {
            return this.hide(VanillaSky.values());
        }

        /** Shows the skybox by itself in these dimensions. */
        @SafeVarargs
        public final Builder dimensions(ResourceKey<Level>... dimensions) {
            this.dimensions.addAll(List.of(dimensions));
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder fade(int fadeInTicks, int fadeOutTicks) {
            this.fadeIn = Math.max(0, fadeInTicks);
            this.fadeOut = Math.max(0, fadeOutTicks);
            return this;
        }

        /**
         * @throws IllegalStateException if the definition is invalid
         */
        public SkyboxDefinition build() {
            SkyboxDefinition definition = new SkyboxDefinition(this.layers, this.hide, this.dimensions, this.priority, this.fadeIn, this.fadeOut);
            return SkyLayerDefinition.orThrow(validate(definition));
        }
    }
}
