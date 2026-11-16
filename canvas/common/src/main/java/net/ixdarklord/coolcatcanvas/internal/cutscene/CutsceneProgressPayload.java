package net.ixdarklord.coolcatcanvas.internal.cutscene;

import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * A client telling the server how far the cutscene it was asked to play has got: a keyframe reached, or a marker
 * passed.
 *
 * @param keyframe the keyframe's index, or -1 when this is about a marker
 * @param marker   the marker's id, when this is about one
 */
public record CutsceneProgressPayload(Identifier id, int keyframe, Optional<Identifier> marker) implements CustomPacketPayload {
    public static final Type<CutsceneProgressPayload> TYPE = new Type<>(CoolCatCanvas.rl("cutscene_progress"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CutsceneProgressPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                Identifier.STREAM_CODEC.encode(buf, payload.id);
                buf.writeVarInt(payload.keyframe + 1);
                if (payload.keyframe < 0) Identifier.STREAM_CODEC.encode(buf, payload.marker.orElseThrow());
            },
            buf -> {
                Identifier id = Identifier.STREAM_CODEC.decode(buf);
                int keyframe = buf.readVarInt() - 1;
                return new CutsceneProgressPayload(id, keyframe, keyframe < 0 ? Optional.of(Identifier.STREAM_CODEC.decode(buf)) : Optional.empty());
            });

    public static CutsceneProgressPayload keyframe(Identifier id, int index) {
        return new CutsceneProgressPayload(id, index, Optional.empty());
    }

    public static CutsceneProgressPayload marker(Identifier id, Identifier marker) {
        return new CutsceneProgressPayload(id, -1, Optional.of(marker));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
