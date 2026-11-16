package net.ixdarklord.coolcatcanvas.internal.cutscene;

import net.ixdarklord.coolcatcanvas.api.cutscene.Cutscene;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * The server telling a client to play a cutscene, or to stop the one playing.
 *
 * @param id       the cutscene to play (the name the client reports back when it ends), or empty to stop
 * @param cutscene the cutscene itself, when the client isn't expected to have it registered
 * @param origin   where its positions are measured from, in place of its own anchor
 * @param yaw      with an origin: the turn of its space around the vertical
 */
public record CutscenePayload(Optional<Identifier> id, Optional<Cutscene> cutscene, Optional<Vec3> origin, float yaw) implements CustomPacketPayload {
    public static final Type<CutscenePayload> TYPE = new Type<>(CoolCatCanvas.rl("cutscene"));
    private static final StreamCodec<RegistryFriendlyByteBuf, Cutscene> CUTSCENE = ByteBufCodecs.fromCodecWithRegistries(Cutscene.CODEC);
    public static final StreamCodec<RegistryFriendlyByteBuf, CutscenePayload> STREAM_CODEC = StreamCodec.of(CutscenePayload::write, CutscenePayload::read);

    public static CutscenePayload stop() {
        return new CutscenePayload(Optional.empty(), Optional.empty(), Optional.empty(), 0.0F);
    }

    private static void write(RegistryFriendlyByteBuf buf, CutscenePayload payload) {
        buf.writeBoolean(payload.id.isPresent());
        if (payload.id.isEmpty()) return;
        Identifier.STREAM_CODEC.encode(buf, payload.id.get());
        buf.writeBoolean(payload.cutscene.isPresent());
        if (payload.cutscene.isPresent()) CUTSCENE.encode(buf, payload.cutscene.get());
        buf.writeBoolean(payload.origin.isPresent());
        if (payload.origin.isPresent()) {
            Vec3.STREAM_CODEC.encode(buf, payload.origin.get());
            buf.writeFloat(payload.yaw);
        }
    }

    private static CutscenePayload read(RegistryFriendlyByteBuf buf) {
        if (!buf.readBoolean()) return stop();
        Identifier id = Identifier.STREAM_CODEC.decode(buf);
        Optional<Cutscene> cutscene = buf.readBoolean() ? Optional.of(CUTSCENE.decode(buf)) : Optional.empty();
        boolean placed = buf.readBoolean();
        Optional<Vec3> origin = placed ? Optional.of(Vec3.STREAM_CODEC.decode(buf)) : Optional.empty();
        return new CutscenePayload(Optional.of(id), cutscene, origin, placed ? buf.readFloat() : 0.0F);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
