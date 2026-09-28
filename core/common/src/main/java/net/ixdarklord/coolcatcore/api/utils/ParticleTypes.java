package net.ixdarklord.coolcatcore.api.utils;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import net.ixdarklord.coolcatcore.api.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
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
     * <p>
     * On 1.20.1, particle options write themselves to the network ({@link ParticleOptions#writeToNetwork}): make it
     * write what {@code packetCodec} reads, e.g. {@code PACKET_CODEC.encode(buf, this)}. In commands, the options are
     * given as SNBT that {@code codec} reads.
     *
     * @param codec The codec for serialization.
     * @param packetCodec The packet codec for network serialization.
     */
    public static <T extends ParticleOptions> ParticleType<T> complex(final MapCodec<T> codec, final StreamCodec<? super FriendlyByteBuf, T> packetCodec) {
        return complex(false, codec, packetCodec);
    }

    /**
     * Creates a new particle type with a custom factory and codecs for packet/data serialization. See
     * {@link #complex(MapCodec, StreamCodec)}.
     *
     * @param alwaysSpawn True to always spawn the particle regardless of distance.
     * @param codec The codec for serialization.
     * @param packetCodec The packet codec for network serialization.
     */
    public static <T extends ParticleOptions> ParticleType<T> complex(boolean alwaysSpawn, final MapCodec<T> codec, final StreamCodec<? super FriendlyByteBuf, T> packetCodec) {
        return complex(alwaysSpawn, type -> codec, type -> packetCodec);
    }

    /**
     * Creates a new particle type with a custom factory and codecs for packet/data serialization.
     * This method is useful when two different {@link ParticleType}s share the same {@link ParticleOptions} implementation.
     * See {@link #complex(MapCodec, StreamCodec)}.
     *
     * @param codecGetter A function that, given the newly created type, returns the codec for serialization.
     * @param packetCodecGetter A function that, given the newly created type, returns the packet codec for network serialization.
     */
    public static <T extends ParticleOptions> ParticleType<T> complex(final Function<ParticleType<T>, MapCodec<T>> codecGetter, final Function<ParticleType<T>, StreamCodec<? super FriendlyByteBuf, T>> packetCodecGetter) {
        return complex(false, codecGetter, packetCodecGetter);
    }

    /**
     * Creates a new particle type with a custom factory and codecs for packet/data serialization.
     * This method is useful when two different {@link ParticleType}s share the same {@link ParticleOptions} implementation.
     * See {@link #complex(MapCodec, StreamCodec)}.
     *
     * @param alwaysSpawn True to always spawn the particle regardless of distance.
     * @param codecGetter A function that, given the newly created type, returns the codec for serialization.
     * @param packetCodecGetter A function that, given the newly created type, returns the packet codec for network serialization.
     */
    public static <T extends ParticleOptions> ParticleType<T> complex(boolean alwaysSpawn, final Function<ParticleType<T>, MapCodec<T>> codecGetter, final Function<ParticleType<T>, StreamCodec<? super FriendlyByteBuf, T>> packetCodecGetter) {
        @SuppressWarnings("deprecation")
        ParticleOptions.Deserializer<T> deserializer = new ParticleOptions.Deserializer<>() {
            @Override
            public @NotNull T fromCommand(@NotNull ParticleType<T> type, @NotNull StringReader reader) throws CommandSyntaxException {
                reader.expect(' ');
                CompoundTag tag = new TagParser(reader).readStruct();
                return codecGetter.apply(type).codec().parse(NbtOps.INSTANCE, tag).result()
                        .orElseThrow(() -> INVALID_OPTIONS.createWithContext(reader));
            }

            @Override
            public @NotNull T fromNetwork(@NotNull ParticleType<T> type, @NotNull FriendlyByteBuf buf) {
                return packetCodecGetter.apply(type).decode(buf);
            }
        };
        return new ParticleType<>(alwaysSpawn, deserializer) {
            @Override
            public @NotNull Codec<T> codec() {
                return codecGetter.apply(this).codec();
            }
        };
    }

    /**
     * Creates a new particle type the 1.20.1 way, with a codec and a deserializer for network and command data.
     *
     * @param codec The codec for serialization.
     * @param deserializer The particle deserializer for network and command serialization.
     */
    @SuppressWarnings("deprecation")
    public static <T extends ParticleOptions> ParticleType<T> complex(final Codec<T> codec, final ParticleOptions.Deserializer<T> deserializer) {
        return complex(false, codec, deserializer);
    }

    /**
     * Creates a new particle type the 1.20.1 way, with a codec and a deserializer for network and command data.
     *
     * @param alwaysSpawn True to always spawn the particle regardless of distance.
     * @param codec The codec for serialization.
     * @param deserializer The particle deserializer for network and command serialization.
     */
    @SuppressWarnings("deprecation")
    public static <T extends ParticleOptions> ParticleType<T> complex(boolean alwaysSpawn, final Codec<T> codec, final ParticleOptions.Deserializer<T> deserializer) {
        return new ParticleType<>(alwaysSpawn, deserializer) {
            @Override
            public @NotNull Codec<T> codec() {
                return codec;
            }
        };
    }

    private static final SimpleCommandExceptionType INVALID_OPTIONS = new SimpleCommandExceptionType(Component.literal("Invalid particle options"));
}
