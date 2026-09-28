package net.ixdarklord.coolcatcanvas.internal.effect;


import net.ixdarklord.coolcatcore.api.network.codec.ByteBufCodecs;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.network.FriendlyByteBuf;
import net.ixdarklord.coolcatcore.api.network.codec.StreamCodec;
import net.ixdarklord.coolcatcore.api.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The server telling a client what to do with one of its registered screen effects.
 *
 * @param ticks   the duration of {@link Action#ENABLE_FOR}, or of the transition of {@link Action#SET_STRENGTH} and
 *                {@link Action#SET_UNIFORM} (0 for instant)
 * @param uniform the uniform {@link Action#SET_UNIFORM} changes, empty otherwise
 * @param values  the strength (first value) or uniform components
 */
public record ScreenEffectPayload(ResourceLocation effect, Action action, int ticks, String uniform, float[] values) implements CustomPacketPayload {
    public static final Type<ScreenEffectPayload> TYPE = new Type<>(CoolCatCanvas.rl("screen_effect"));
    private static final int MAX_VALUES = 16;
    public static final StreamCodec<FriendlyByteBuf, ScreenEffectPayload> STREAM_CODEC = StreamCodec.of(ScreenEffectPayload::write, ScreenEffectPayload::read);

    public ScreenEffectPayload {
        if (values.length > MAX_VALUES) throw new IllegalArgumentException("At most " + MAX_VALUES + " values, got " + values.length);
    }

    private static void write(FriendlyByteBuf buf, ScreenEffectPayload payload) {
        ByteBufCodecs.RESOURCE_LOCATION.encode(buf, payload.effect);
        buf.writeEnum(payload.action);
        buf.writeVarInt(payload.ticks);
        buf.writeUtf(payload.uniform, 256);
        buf.writeVarInt(payload.values.length);
        for (float value : payload.values) buf.writeFloat(value);
    }

    private static ScreenEffectPayload read(FriendlyByteBuf buf) {
        ResourceLocation effect = ByteBufCodecs.RESOURCE_LOCATION.decode(buf);
        Action action = buf.readEnum(Action.class);
        int ticks = buf.readVarInt();
        String uniform = buf.readUtf(256);
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_VALUES) throw new IllegalArgumentException("Too many values: " + count);
        float[] values = new float[count];
        for (int i = 0; i < count; i++) values[i] = buf.readFloat();
        return new ScreenEffectPayload(effect, action, ticks, uniform, values);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action {
        ENABLE,
        DISABLE,
        ENABLE_FOR,
        ENABLE_INSTANTLY,
        DISABLE_INSTANTLY,
        SET_STRENGTH,
        SET_UNIFORM,
        RESET_UNIFORMS
    }
}
