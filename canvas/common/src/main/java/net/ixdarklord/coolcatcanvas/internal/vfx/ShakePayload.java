package net.ixdarklord.coolcatcanvas.internal.vfx;

import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** The server shaking a client's camera. A radius of 0 means the shake has no place: it is felt in full. */
public record ShakePayload(Vec3 origin, float radius, float degrees, int ticks) implements CustomPacketPayload {
    public static final Type<ShakePayload> TYPE = new Type<>(CoolCatCanvas.rl("shake"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShakePayload> STREAM_CODEC = StreamCodec.of(ShakePayload::write, ShakePayload::read);

    private static void write(RegistryFriendlyByteBuf buf, ShakePayload payload) {
        buf.writeDouble(payload.origin.x);
        buf.writeDouble(payload.origin.y);
        buf.writeDouble(payload.origin.z);
        buf.writeFloat(payload.radius);
        buf.writeFloat(payload.degrees);
        buf.writeVarInt(payload.ticks);
    }

    private static ShakePayload read(RegistryFriendlyByteBuf buf) {
        return new ShakePayload(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat(), buf.readFloat(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
