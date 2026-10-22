package net.ixdarklord.coolcatcanvas.internal.vfx;

import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.network.FriendlyByteBuf;
import net.ixdarklord.coolcatcore.api.network.codec.StreamCodec;
import net.ixdarklord.coolcatcore.api.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** The server opening one of a client's registered rifts, or closing one it opened. */
public record RiftPayload(boolean open, UUID id, ResourceLocation spec, Vec3 position, int openTicks) implements CustomPacketPayload {
    public static final Type<RiftPayload> TYPE = new Type<>(CoolCatCanvas.rl("rift"));
    public static final StreamCodec<FriendlyByteBuf, RiftPayload> STREAM_CODEC = StreamCodec.of(RiftPayload::write, RiftPayload::read);

    private static void write(FriendlyByteBuf buf, RiftPayload payload) {
        buf.writeBoolean(payload.open);
        buf.writeUUID(payload.id);
        if (payload.open) {
            buf.writeResourceLocation(payload.spec);
            buf.writeDouble(payload.position.x);
            buf.writeDouble(payload.position.y);
            buf.writeDouble(payload.position.z);
            buf.writeVarInt(payload.openTicks + 1);
        }
    }

    private static RiftPayload read(FriendlyByteBuf buf) {
        boolean open = buf.readBoolean();
        UUID id = buf.readUUID();
        if (!open) return new RiftPayload(false, id, CoolCatCanvas.rl("rift"), Vec3.ZERO, -1);
        return new RiftPayload(true, id, buf.readResourceLocation(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readVarInt() - 1);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
