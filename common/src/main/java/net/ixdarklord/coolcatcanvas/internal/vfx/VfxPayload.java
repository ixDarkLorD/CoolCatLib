package net.ixdarklord.coolcatcanvas.internal.vfx;

import net.ixdarklord.coolcatcanvas.api.vfx.VfxParams;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** The server telling a client to play one of its registered world effects. */
public record VfxPayload(Identifier effect, VfxParams params) implements CustomPacketPayload {
    public static final Type<VfxPayload> TYPE = new Type<>(CoolCatCanvas.rl("vfx"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VfxPayload> STREAM_CODEC = StreamCodec.of(VfxPayload::write, VfxPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, VfxPayload payload) {
        Identifier.STREAM_CODEC.encode(buf, payload.effect);
        VfxParams params = payload.params;
        buf.writeDouble(params.position().x);
        buf.writeDouble(params.position().y);
        buf.writeDouble(params.position().z);
        // The second point travels as an offset: most effects don't use it, and a float is plenty for the rest.
        buf.writeFloat((float) (params.target().x - params.position().x));
        buf.writeFloat((float) (params.target().y - params.position().y));
        buf.writeFloat((float) (params.target().z - params.position().z));
        buf.writeMedium(params.color() & 0xFFFFFF);
        buf.writeFloat(params.scale());
    }

    private static VfxPayload read(RegistryFriendlyByteBuf buf) {
        Identifier effect = Identifier.STREAM_CODEC.decode(buf);
        Vec3 position = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3 target = position.add(buf.readFloat(), buf.readFloat(), buf.readFloat());
        int color = buf.readUnsignedMedium();
        float scale = buf.readFloat();
        return new VfxPayload(effect, new VfxParams(position, target, color, scale));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
