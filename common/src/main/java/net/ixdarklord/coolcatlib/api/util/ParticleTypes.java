package net.ixdarklord.coolcatlib.api.util;

import com.mojang.serialization.Codec;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import org.jetbrains.annotations.NotNull;

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
     * @param deserializer The particle deserializer for network and command serialization.
     */
    public static <T extends ParticleOptions> ParticleType<T> complex(final Codec<T> codec, ParticleOptions.Deserializer<T> deserializer) {
        return complex(false, codec, deserializer);
    }

    /**
     * Creates a new particle type with a custom factory and codecs for packet/data serialization.
     *
     * @param alwaysSpawn True to always spawn the particle regardless of distance.
     * @param codec The codec for serialization.
     * @param deserializer The particle deserializer for network and command serialization.
     */
    public static <T extends ParticleOptions> ParticleType<T> complex(boolean alwaysSpawn, final Codec<T> codec, ParticleOptions.Deserializer<T> deserializer) {
        return new ParticleType<>(alwaysSpawn, deserializer) {

            @Override
            public @NotNull Codec<T> codec() {
                return codec;
            }
        };
    }
}
