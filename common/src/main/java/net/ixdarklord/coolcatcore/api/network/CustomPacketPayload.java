package net.ixdarklord.coolcatcore.api.network;

import io.netty.buffer.ByteBuf;
import net.ixdarklord.coolcatcore.api.network.codec.StreamCodec;
import net.ixdarklord.coolcatcore.api.network.codec.StreamDecoder;
import net.ixdarklord.coolcatcore.api.network.codec.StreamMemberEncoder;
import net.minecraft.resources.ResourceLocation;

/**
 * A payload sent through {@link Network}: 1.20.5+'s {@code CustomPacketPayload}, which 1.20.1 doesn't have. Each
 * payload type has an id (its {@link Type}) and a {@link StreamCodec}; the loaders send it as a custom packet on the
 * id's channel.
 */
public interface CustomPacketPayload {
    Type<? extends CustomPacketPayload> type();

    static <B extends ByteBuf, T extends CustomPacketPayload> StreamCodec<B, T> codec(StreamMemberEncoder<B, T> encoder, StreamDecoder<B, T> decoder) {
        return StreamCodec.ofMember(encoder, decoder);
    }

    static <T extends CustomPacketPayload> Type<T> createType(String id) {
        return new Type<>(new ResourceLocation(id));
    }

    record Type<T extends CustomPacketPayload>(ResourceLocation id) {
    }

    record TypeAndCodec<B extends ByteBuf, T extends CustomPacketPayload>(Type<T> type, StreamCodec<B, T> codec) {
    }
}
