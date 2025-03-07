package net.ixdarklord.coolcatlib.api.util;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public final class ParticleTypes {
    public static SimpleParticleType simple() {
        return simple(false);
    }

    /**
     * Creates a new, default particle type for the given id.
     *
     * @param alwaysSpawn True to always spawn the particle regardless of distance.
     */
    public static SimpleParticleType simple(boolean alwaysSpawn) {
        return new SimpleParticleType(alwaysSpawn) { };
    }

    /**
     * Creates a new particle type with a custom factory and codecs for packet/data serialization.
     *
     * @param codec The codec for serialization.
     * @param packetCodec The packet codec for network serialization.
     */
    public static <T extends ParticleOptions> ParticleType<T> complex(final MapCodec<T> codec, final StreamCodec<? super RegistryFriendlyByteBuf, T> packetCodec) {
        return complex(false, codec, packetCodec);
    }

    /**
     * Creates a new particle type with a custom factory and codecs for packet/data serialization.
     *
     * @param alwaysSpawn True to always spawn the particle regardless of distance.
     * @param codec The codec for serialization.
     * @param packetCodec The packet codec for network serialization.
     */
    public static <T extends ParticleOptions> ParticleType<T> complex(boolean alwaysSpawn, final MapCodec<T> codec, final StreamCodec<? super RegistryFriendlyByteBuf, T> packetCodec) {
        return new ParticleType<>(alwaysSpawn) {
            @Override
            public @NotNull MapCodec<T> codec() {
                return codec;
            }

            @Override
            public @NotNull StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec() {
                return packetCodec;
            }
        };
    }

    /**
     * Creates a new particle type with a custom factory and codecs for packet/data serialization.
     * This method is useful when two different {@link ParticleType}s share the same {@link ParticleOptions} implementation.
     *
     * @param codecGetter A function that, given the newly created type, returns the codec for serialization.
     * @param packetCodecGetter A function that, given the newly created type, returns the packet codec for network serialization.
     */
    public static <T extends ParticleOptions> ParticleType<T> complex(final Function<ParticleType<T>, MapCodec<T>> codecGetter, final Function<ParticleType<T>, StreamCodec<? super RegistryFriendlyByteBuf, T>> packetCodecGetter) {
        return complex(false, codecGetter, packetCodecGetter);
    }

    /**
     * Creates a new particle type with a custom factory and codecs for packet/data serialization.
     * This method is useful when two different {@link ParticleType}s share the same {@link ParticleOptions} implementation.
     *
     * @param alwaysSpawn True to always spawn the particle regardless of distance.
     * @param codecGetter A function that, given the newly created type, returns the codec for serialization.
     * @param packetCodecGetter A function that, given the newly created type, returns the packet codec for network serialization.
     */
    public static <T extends ParticleOptions> ParticleType<T> complex(boolean alwaysSpawn, final Function<ParticleType<T>, MapCodec<T>> codecGetter, final Function<ParticleType<T>, StreamCodec<? super RegistryFriendlyByteBuf, T>> packetCodecGetter) {
        return new ParticleType<>(alwaysSpawn) {
            @Override
            public @NotNull MapCodec<T> codec() {
                return codecGetter.apply(this);
            }

            @Override
            public @NotNull StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec() {
                return packetCodecGetter.apply(this);
            }
        };
    }
}
