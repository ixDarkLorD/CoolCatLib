package net.ixdarklord.coolcatcanvas.internal.armature;

import net.ixdarklord.coolcatcanvas.api.armature.Motion;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

/**
 * The server telling clients to do something to an armature: play a motion on a layer, stop a layer, or set a Molang
 * variable. The armature belongs to an entity, a block entity, or the stack in an entity's hand.
 */
public record ArmatureCommandPayload(Target target, int entityId, BlockPos pos, boolean offhand, Action action, String layer,
                                @Nullable Motion motion, float fade, String variable, double value) implements CustomPacketPayload {
    public static final Type<ArmatureCommandPayload> TYPE = new Type<>(CoolCatCanvas.rl("armature_command"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ArmatureCommandPayload> STREAM_CODEC = StreamCodec.of(ArmatureCommandPayload::write, ArmatureCommandPayload::read);

    public enum Target { ENTITY, BLOCK, HELD }

    public enum Action { PLAY, STOP, VARIABLE }

    private static void write(RegistryFriendlyByteBuf buf, ArmatureCommandPayload payload) {
        buf.writeEnum(payload.target);
        if (payload.target == Target.BLOCK) buf.writeBlockPos(payload.pos);
        else buf.writeVarInt(payload.entityId);
        if (payload.target == Target.HELD) buf.writeBoolean(payload.offhand);
        buf.writeEnum(payload.action);
        switch (payload.action) {
            case PLAY -> {
                buf.writeUtf(payload.layer);
                Motion.STREAM_CODEC.encode(buf, payload.motion);
            }
            case STOP -> {
                buf.writeUtf(payload.layer);
                buf.writeFloat(payload.fade);
            }
            case VARIABLE -> {
                buf.writeUtf(payload.variable);
                buf.writeDouble(payload.value);
            }
        }
    }

    private static ArmatureCommandPayload read(RegistryFriendlyByteBuf buf) {
        Target target = buf.readEnum(Target.class);
        BlockPos pos = target == Target.BLOCK ? buf.readBlockPos() : BlockPos.ZERO;
        int entityId = target == Target.BLOCK ? 0 : buf.readVarInt();
        boolean offhand = target == Target.HELD && buf.readBoolean();
        Action action = buf.readEnum(Action.class);
        return switch (action) {
            case PLAY -> new ArmatureCommandPayload(target, entityId, pos, offhand, action, buf.readUtf(), Motion.STREAM_CODEC.decode(buf), 0.0F, "", 0.0);
            case STOP -> new ArmatureCommandPayload(target, entityId, pos, offhand, action, buf.readUtf(), null, buf.readFloat(), "", 0.0);
            case VARIABLE -> new ArmatureCommandPayload(target, entityId, pos, offhand, action, "", null, 0.0F, buf.readUtf(), buf.readDouble());
        };
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
