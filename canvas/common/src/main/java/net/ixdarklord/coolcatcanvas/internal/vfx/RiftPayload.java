package net.ixdarklord.coolcatcanvas.internal.vfx;

import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** The server opening one of a client's registered rifts, or closing one it opened. */
public record RiftPayload(boolean open, UUID id, Identifier spec, Vec3 position, int openTicks) implements CustomPacketPayload {
    public static final Type<RiftPayload> TYPE = new Type<>(CoolCatCanvas.rl("rift"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RiftPayload> STREAM_CODEC = StreamCodec.of(RiftPayload::write, RiftPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, RiftPayload payload) {
        buf.writeBoolean(payload.open);
        UUIDUtil.STREAM_CODEC.encode(buf, payload.id);
        if (payload.open) {
            Identifier.STREAM_CODEC.encode(buf, payload.spec);
            buf.writeDouble(payload.position.x);
            buf.writeDouble(payload.position.y);
            buf.writeDouble(payload.position.z);
            buf.writeVarInt(payload.openTicks + 1);
        }
    }

    private static RiftPayload read(RegistryFriendlyByteBuf buf) {
        boolean open = buf.readBoolean();
        UUID id = UUIDUtil.STREAM_CODEC.decode(buf);
        if (!open) return new RiftPayload(false, id, CoolCatCanvas.rl("rift"), Vec3.ZERO, -1);
        return new RiftPayload(true, id, Identifier.STREAM_CODEC.decode(buf), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readVarInt() - 1);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
