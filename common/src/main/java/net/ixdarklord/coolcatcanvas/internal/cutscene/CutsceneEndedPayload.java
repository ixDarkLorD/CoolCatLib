package net.ixdarklord.coolcatcanvas.internal.cutscene;

import net.ixdarklord.coolcatcanvas.api.cutscene.CutsceneEndReason;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A client telling the server that the cutscene it was asked to play has ended, and why. */
public record CutsceneEndedPayload(Identifier id, CutsceneEndReason reason) implements CustomPacketPayload {
    public static final Type<CutsceneEndedPayload> TYPE = new Type<>(CoolCatCanvas.rl("cutscene_ended"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CutsceneEndedPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                Identifier.STREAM_CODEC.encode(buf, payload.id);
                buf.writeEnum(payload.reason);
            },
            buf -> new CutsceneEndedPayload(Identifier.STREAM_CODEC.decode(buf), buf.readEnum(CutsceneEndReason.class)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
