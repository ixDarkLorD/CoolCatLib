package net.ixdarklord.coolcatcanvas.api.client.sky;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.ExtraCodecs;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One layer of a {@link SkyboxDefinition}: an image, a gradient or a shader drawn over the sky, and how it moves and
 * fades. In JSON (only {@code type} and what the type needs are required):
 * <pre>{@code
 * {
 *   "name": "stars",
 *   "type": "panorama",
 *   "texture": "mymod:textures/sky/stars.png",
 *   "blend": "additive",
 *   "tint": "#FFFFFFFF",
 *   "stage": "behind_celestials",
 *   "orientation": [0, 0, 0],
 *   "rotation": {"axis": [0, 0, 1], "speed": 1, "clock": "star_angle"},
 *   "animation": {"frames": 4, "frame_time": 10, "interpolate": true},
 *   "day_fade": {"fade_in": [11000, 13000], "fade_out": [23000, 1000]},
 *   "rain_fade": 1,
 *   "fog": 1,
 *   "blur": true,
 *   "params": [[0.5, 1, 0, 0], "#FF3366CC"]
 * }
 * }</pre>
 *
 * @param name           how the layer is found at runtime, with {@link Skybox#layer}; unnamed layers are named
 *                       {@code layer_<index>}
 * @param texture        the full path of the image, {@code <namespace>:textures/<path>.png}
 * @param fragmentShader replaces the type's built-in fragment shader, from
 *                       {@code assets/<namespace>/shaders/<path>.fsh}; see {@code coolcatcanvas:sky.glsl}
 * @param tint           multiplies the layer's colors, packed ARGB: its alpha is the layer's base opacity
 * @param orientation    a fixed rotation in degrees, applied as yaw ({@code y}), then pitch ({@code x}), then roll
 *                       ({@code z})
 * @param rotation       a rotation over time, applied after the orientation
 * @param animation      plays the texture as a flipbook of frames stacked vertically
 * @param dayFade        shows the layer only during part of the day
 * @param rainFade       how much rain (and thunder) fades the layer out, from 0 (none) to 1 (gone in full rain)
 * @param fog            how much the layer blends into the fog color towards the horizon and in water, lava or snow,
 *                       from 0 (never) to 1 (as much as vanilla's sky)
 * @param blur           linear texture filtering; false keeps pixels sharp
 * @param size           a sprite's width, in degrees of the sky
 * @param params         up to 4 values the shader reads as {@code SkyParams[i]}: 1 to 4 numbers, or a color
 */
public record SkyLayerDefinition(
        String name,
        SkyLayerType type,
        Optional<Identifier> texture,
        Optional<Identifier> fragmentShader,
        SkyBlend blend,
        int tint,
        SkyLayerStage stage,
        Vector3fc orientation,
        Optional<Rotation> rotation,
        Optional<Animation> animation,
        Optional<DayFade> dayFade,
        float rainFade,
        float fog,
        boolean blur,
        float size,
        List<Vector4fc> params
) {
    public static final int MAX_PARAMS = 4;
    public static final int WHITE = 0xFFFFFFFF;
    private static final Vector4fc ZERO = new Vector4f();

    /** A color: {@code "#AARRGGBB"}, {@code "#RRGGBB"}, a packed ARGB int, or 3 or 4 floats. */
    public static final Codec<Integer> COLOR_CODEC = Codec.withAlternative(ExtraCodecs.STRING_ARGB_COLOR, ExtraCodecs.STRING_RGB_COLOR);
    /** 1 to 4 numbers (the rest are 0), or a color as its RGBA components. */
    public static final Codec<Vector4fc> PARAM_CODEC = Codec.withAlternative(
            Codec.FLOAT.listOf(1, 4).xmap(SkyLayerDefinition::vector, vector -> List.of(vector.x(), vector.y(), vector.z(), vector.w())),
            COLOR_CODEC.xmap(SkyLayerDefinition::colorVector, vector -> ARGB.colorFromFloat(vector.w(), vector.x(), vector.y(), vector.z()))
    );

    public static final Codec<SkyLayerDefinition> CODEC = RecordCodecBuilder.<SkyLayerDefinition>create(i -> i.group(
            Codec.STRING.optionalFieldOf("name", "").forGetter(SkyLayerDefinition::name),
            SkyLayerType.CODEC.fieldOf("type").forGetter(SkyLayerDefinition::type),
            Identifier.CODEC.optionalFieldOf("texture").forGetter(SkyLayerDefinition::texture),
            Identifier.CODEC.optionalFieldOf("fragment_shader").forGetter(SkyLayerDefinition::fragmentShader),
            SkyBlend.CODEC.optionalFieldOf("blend", SkyBlend.ALPHA).forGetter(SkyLayerDefinition::blend),
            COLOR_CODEC.optionalFieldOf("tint", WHITE).forGetter(SkyLayerDefinition::tint),
            SkyLayerStage.CODEC.optionalFieldOf("stage", SkyLayerStage.BEHIND_CELESTIALS).forGetter(SkyLayerDefinition::stage),
            ExtraCodecs.VECTOR3F.optionalFieldOf("orientation", new Vector3f()).forGetter(SkyLayerDefinition::orientation),
            Rotation.CODEC.optionalFieldOf("rotation").forGetter(SkyLayerDefinition::rotation),
            Animation.CODEC.optionalFieldOf("animation").forGetter(SkyLayerDefinition::animation),
            DayFade.CODEC.optionalFieldOf("day_fade").forGetter(SkyLayerDefinition::dayFade),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("rain_fade", 0.0F).forGetter(SkyLayerDefinition::rainFade),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("fog", 1.0F).forGetter(SkyLayerDefinition::fog),
            Codec.BOOL.optionalFieldOf("blur", true).forGetter(SkyLayerDefinition::blur),
            Codec.floatRange(0.01F, 179.0F).optionalFieldOf("size", 20.0F).forGetter(SkyLayerDefinition::size),
            PARAM_CODEC.listOf(0, MAX_PARAMS).optionalFieldOf("params", List.of()).forGetter(SkyLayerDefinition::params)
    ).apply(i, SkyLayerDefinition::new)).validate(SkyLayerDefinition::validate);

    public SkyLayerDefinition {
        orientation = new Vector3f(orientation);
        params = params.stream().<Vector4fc>map(Vector4f::new).toList();
    }

    // ---- Builders ----

    /** A cube of six faces around the sky, from one image; see {@link SkyLayerType#CUBEMAP} for the layout. */
    public static Builder cubemap(Identifier texture) {
        return new Builder(SkyLayerType.CUBEMAP).texture(texture);
    }

    /** An equirectangular panorama around the sky. */
    public static Builder panorama(Identifier texture) {
        return new Builder(SkyLayerType.PANORAMA).texture(texture);
    }

    /** An image in the sky, {@code size} degrees wide, at the zenith until oriented or rotated. */
    public static Builder sprite(Identifier texture, float size) {
        return new Builder(SkyLayerType.SPRITE).texture(texture).size(size);
    }

    /**
     * A vertical gradient over the sky, packed ARGB colors.
     *
     * @param sharpness 1 fades linearly from the horizon, higher keeps the horizon color to a thinner band
     */
    public static Builder gradient(int zenith, int horizon, int nadir, float sharpness) {
        return new Builder(SkyLayerType.GRADIENT).paramColor(0, zenith).paramColor(1, horizon).paramColor(2, nadir).param(3, sharpness);
    }

    /** A procedural sky drawn by a fragment shader of your own, from {@code assets/<namespace>/shaders/<path>.fsh}. */
    public static Builder shader(Identifier fragmentShader) {
        return new Builder(SkyLayerType.SHADER).fragmentShader(fragmentShader);
    }

    public SkyLayerDefinition withName(String name) {
        return new SkyLayerDefinition(name, this.type, this.texture, this.fragmentShader, this.blend, this.tint, this.stage, this.orientation,
                this.rotation, this.animation, this.dayFade, this.rainFade, this.fog, this.blur, this.size, this.params);
    }

    /** Param {@code index}, or zero if the definition doesn't set it. */
    public Vector4fc param(int index) {
        return index < this.params.size() ? this.params.get(index) : ZERO;
    }

    private static DataResult<SkyLayerDefinition> validate(SkyLayerDefinition layer) {
        if (layer.type.needsTexture() && layer.texture.isEmpty()) {
            return DataResult.error(() -> "A " + layer.type.getSerializedName() + " sky layer needs a \"texture\"");
        }
        if (layer.type == SkyLayerType.SHADER && layer.fragmentShader.isEmpty()) {
            return DataResult.error(() -> "A shader sky layer needs a \"fragment_shader\"");
        }
        if (layer.animation.isPresent() && layer.texture.isEmpty()) {
            return DataResult.error(() -> "An animated sky layer needs a \"texture\" holding its frames");
        }
        if (layer.params.size() > MAX_PARAMS) {
            return DataResult.error(() -> "A sky layer takes at most " + MAX_PARAMS + " params");
        }
        return DataResult.success(layer);
    }

    private static Vector4fc vector(List<Float> values) {
        Vector4f vector = new Vector4f();
        for (int i = 0; i < values.size(); i++) vector.setComponent(i, values.get(i));
        return vector;
    }

    private static Vector4fc colorVector(int argb) {
        return new Vector4f(ARGB.redFloat(argb), ARGB.greenFloat(argb), ARGB.blueFloat(argb), ARGB.alphaFloat(argb));
    }

    /**
     * Turns the layer around an axis as its clock moves.
     *
     * @param axis  the axis, in world space; {@code [0, 1, 0]} spins around the zenith, {@code [0, 0, 1]} follows the
     *              path of vanilla's sun
     * @param speed degrees per unit of the clock (per second, per day, or per degree of vanilla's angle)
     */
    public record Rotation(Vector3fc axis, float speed, SkyClock clock) {
        public static final Codec<Rotation> CODEC = RecordCodecBuilder.<Rotation>create(i -> i.group(
                ExtraCodecs.VECTOR3F.optionalFieldOf("axis", new Vector3f(0.0F, 1.0F, 0.0F)).forGetter(Rotation::axis),
                Codec.FLOAT.optionalFieldOf("speed", 1.0F).forGetter(Rotation::speed),
                SkyClock.CODEC.optionalFieldOf("clock", SkyClock.REAL_TIME).forGetter(Rotation::clock)
        ).apply(i, Rotation::new)).validate(rotation -> rotation.axis.lengthSquared() > 1.0E-6F
                ? DataResult.success(rotation)
                : DataResult.error(() -> "A sky layer's rotation axis can't be zero"));

        public Rotation {
            axis = new Vector3f(axis).normalize();
        }

        /** Follows vanilla's sun, rising in the east: for a sprite, or a sky that turns with the day. */
        public static Rotation withSun() {
            return new Rotation(new Vector3f(0.0F, 0.0F, 1.0F), 1.0F, SkyClock.SUN_ANGLE);
        }

        /** Follows vanilla's moon. */
        public static Rotation withMoon() {
            return new Rotation(new Vector3f(0.0F, 0.0F, 1.0F), 1.0F, SkyClock.MOON_ANGLE);
        }

        /** Follows vanilla's stars. */
        public static Rotation withStars() {
            return new Rotation(new Vector3f(0.0F, 0.0F, 1.0F), 1.0F, SkyClock.STAR_ANGLE);
        }
    }

    /**
     * Plays the texture as a flipbook: {@code frames} images of equal height stacked top to bottom.
     *
     * @param frameTime   ticks each frame shows for (20 a second)
     * @param interpolate cross-fades from each frame into the next instead of cutting
     * @param clock       {@link SkyClock#GAME_TIME} (the default) or {@link SkyClock#REAL_TIME}
     */
    public record Animation(int frames, float frameTime, boolean interpolate, SkyClock clock) {
        public static final Codec<Animation> CODEC = RecordCodecBuilder.<Animation>create(i -> i.group(
                ExtraCodecs.POSITIVE_INT.fieldOf("frames").forGetter(Animation::frames),
                Codec.floatRange(0.001F, Float.MAX_VALUE).optionalFieldOf("frame_time", 1.0F).forGetter(Animation::frameTime),
                Codec.BOOL.optionalFieldOf("interpolate", false).forGetter(Animation::interpolate),
                SkyClock.CODEC.optionalFieldOf("clock", SkyClock.GAME_TIME).forGetter(Animation::clock)
        ).apply(i, Animation::new)).validate(animation -> animation.clock.isTime()
                ? DataResult.success(animation)
                : DataResult.error(() -> "A sky animation runs on real_time or game_time, not " + animation.clock.getSerializedName()));
    }

    /**
     * Shows the layer during part of the day: it fades in over {@code [startFadeIn, endFadeIn]}, stays, then fades
     * out over {@code [startFadeOut, endFadeOut]}. Times are ticks of the day, 0 to 24000 (0 is sunrise, 6000 noon,
     * 13000 dusk, 18000 midnight), and may wrap around midnight.
     */
    public record DayFade(int startFadeIn, int endFadeIn, int startFadeOut, int endFadeOut) {
        public static final int DAY_LENGTH = 24000;
        private static final Codec<List<Integer>> RANGE = ExtraCodecs.intRange(0, DAY_LENGTH).listOf(2, 2);
        public static final Codec<DayFade> CODEC = RecordCodecBuilder.create(i -> i.group(
                RANGE.fieldOf("fade_in").forGetter(fade -> List.of(fade.startFadeIn, fade.endFadeIn)),
                RANGE.fieldOf("fade_out").forGetter(fade -> List.of(fade.startFadeOut, fade.endFadeOut))
        ).apply(i, (in, out) -> new DayFade(in.get(0), in.get(1), out.get(0), out.get(1))));

        /** Visible at night only, fading around dusk and dawn. */
        public static final DayFade NIGHT = new DayFade(11500, 13500, 22500, 24000);
        /** Visible by day only, fading around dawn and dusk. */
        public static final DayFade DAY = new DayFade(22500, 24000, 11500, 13500);

        /** How visible the layer is at {@code dayTime} ticks into the day, from 0 to 1. */
        public float visibility(float dayTime) {
            float position = wrap(dayTime - this.startFadeIn);
            float fadeIn = wrap(this.endFadeIn - this.startFadeIn);
            float shown = wrap(this.startFadeOut - this.startFadeIn);
            float fadeOut = wrap(this.endFadeOut - this.startFadeOut);
            if (position < fadeIn) return position / fadeIn;
            if (position < shown) return 1.0F;
            if (position < shown + fadeOut) return 1.0F - (position - shown) / fadeOut;
            return 0.0F;
        }

        private static float wrap(float ticks) {
            float wrapped = ticks % DAY_LENGTH;
            return wrapped < 0.0F ? wrapped + DAY_LENGTH : wrapped;
        }
    }

    public static final class Builder {
        private final SkyLayerType type;
        private String name = "";
        private Optional<Identifier> texture = Optional.empty();
        private Optional<Identifier> fragmentShader = Optional.empty();
        private SkyBlend blend = SkyBlend.ALPHA;
        private int tint = WHITE;
        private SkyLayerStage stage = SkyLayerStage.BEHIND_CELESTIALS;
        private final Vector3f orientation = new Vector3f();
        private Optional<Rotation> rotation = Optional.empty();
        private Optional<Animation> animation = Optional.empty();
        private Optional<DayFade> dayFade = Optional.empty();
        private float rainFade;
        private float fog = 1.0F;
        private boolean blur = true;
        private float size = 20.0F;
        private final List<Vector4fc> params = new ArrayList<>();

        private Builder(SkyLayerType type) {
            this.type = type;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder texture(Identifier texture) {
            this.texture = Optional.of(texture);
            return this;
        }

        public Builder fragmentShader(Identifier fragmentShader) {
            this.fragmentShader = Optional.of(fragmentShader);
            return this;
        }

        public Builder blend(SkyBlend blend) {
            this.blend = blend;
            return this;
        }

        /** Packed ARGB; its alpha is the layer's base opacity. */
        public Builder tint(int argb) {
            this.tint = argb;
            return this;
        }

        public Builder stage(SkyLayerStage stage) {
            this.stage = stage;
            return this;
        }

        /** Degrees: yaw ({@code y}), then pitch ({@code x}), then roll ({@code z}). */
        public Builder orientation(float x, float y, float z) {
            this.orientation.set(x, y, z);
            return this;
        }

        public Builder rotation(Rotation rotation) {
            this.rotation = Optional.of(rotation);
            return this;
        }

        public Builder rotation(float axisX, float axisY, float axisZ, float speed, SkyClock clock) {
            return this.rotation(new Rotation(new Vector3f(axisX, axisY, axisZ), speed, clock));
        }

        public Builder animation(int frames, float frameTime, boolean interpolate) {
            this.animation = Optional.of(new Animation(frames, frameTime, interpolate, SkyClock.GAME_TIME));
            return this;
        }

        public Builder animation(Animation animation) {
            this.animation = Optional.of(animation);
            return this;
        }

        public Builder dayFade(DayFade dayFade) {
            this.dayFade = Optional.of(dayFade);
            return this;
        }

        public Builder rainFade(float rainFade) {
            this.rainFade = rainFade;
            return this;
        }

        public Builder fog(float fog) {
            this.fog = fog;
            return this;
        }

        public Builder blur(boolean blur) {
            this.blur = blur;
            return this;
        }

        public Builder size(float degrees) {
            this.size = degrees;
            return this;
        }

        /** Sets {@code SkyParams[index]}; missing components are 0. */
        public Builder param(int index, float... values) {
            if (index < 0 || index >= MAX_PARAMS) throw new IllegalArgumentException("Param index must be 0 to " + (MAX_PARAMS - 1));
            while (this.params.size() <= index) this.params.add(new Vector4f());
            Vector4f vector = new Vector4f();
            for (int i = 0; i < Math.min(4, values.length); i++) vector.setComponent(i, values[i]);
            this.params.set(index, vector);
            return this;
        }

        /** Sets {@code SkyParams[index]} to a packed ARGB color, as RGBA. */
        public Builder paramColor(int index, int argb) {
            return this.param(index, ARGB.redFloat(argb), ARGB.greenFloat(argb), ARGB.blueFloat(argb), ARGB.alphaFloat(argb));
        }

        /**
         * @throws IllegalStateException if the layer is invalid
         */
        public SkyLayerDefinition build() {
            SkyLayerDefinition layer = new SkyLayerDefinition(this.name, this.type, this.texture, this.fragmentShader, this.blend, this.tint, this.stage,
                    this.orientation, this.rotation, this.animation, this.dayFade, this.rainFade, this.fog, this.blur, this.size, this.params);
            return validate(layer).getOrThrow(IllegalStateException::new);
        }
    }
}
