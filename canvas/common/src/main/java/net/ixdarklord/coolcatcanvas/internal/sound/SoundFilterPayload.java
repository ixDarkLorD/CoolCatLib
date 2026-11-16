package net.ixdarklord.coolcatcanvas.internal.sound;

import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The server setting a named sound filter on a client (gain, highs, distortion, reverb), or clearing it ({@code clear},
 * the filter's parts then unused), over {@code fadeTicks}.
 */
public record SoundFilterPayload(ResourceLocation id, boolean clear, float gain, float gainHF, float distortion, float reverb, int fadeTicks) implements CustomPacketPayload {
    public static final Type<SoundFilterPayload> TYPE = new Type<>(CoolCatCanvas.rl("sound_filter"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SoundFilterPayload> STREAM_CODEC = StreamCodec.of(SoundFilterPayload::write, SoundFilterPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, SoundFilterPayload payload) {
        ResourceLocation.STREAM_CODEC.encode(buf, payload.id);
        buf.writeBoolean(payload.clear);
        if (!payload.clear) {
            buf.writeFloat(payload.gain);
            buf.writeFloat(payload.gainHF);
            buf.writeFloat(payload.distortion);
            buf.writeFloat(payload.reverb);
        }
        buf.writeVarInt(payload.fadeTicks);
    }

    private static SoundFilterPayload read(RegistryFriendlyByteBuf buf) {
        ResourceLocation id = ResourceLocation.STREAM_CODEC.decode(buf);
        if (buf.readBoolean()) return new SoundFilterPayload(id, true, 1.0F, 1.0F, 0.0F, 0.0F, buf.readVarInt());
        return new SoundFilterPayload(id, false, buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
