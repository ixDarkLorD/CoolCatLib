package net.ixdarklord.coolcatcanvas.internal.sky;


import net.ixdarklord.coolcatcore.api.network.codec.ByteBufCodecs;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.network.FriendlyByteBuf;
import net.ixdarklord.coolcatcore.api.network.codec.StreamCodec;
import net.ixdarklord.coolcatcore.api.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The server telling a client what to do with one of its skyboxes.
 *
 * @param ticks  the duration of {@link Action#ENABLE_FOR}, or of the transition of the layer actions (0 for instant)
 * @param layer  the layer a layer action changes, empty otherwise
 * @param index  the param {@link Action#SET_LAYER_PARAM} changes
 * @param values the opacity or visibility (first value), or the param's components
 */
public record SkyboxPayload(ResourceLocation skybox, Action action, int ticks, String layer, int index, float[] values) implements CustomPacketPayload {
    public static final Type<SkyboxPayload> TYPE = new Type<>(CoolCatCanvas.rl("skybox"));
    private static final int MAX_VALUES = 4;
    public static final StreamCodec<FriendlyByteBuf, SkyboxPayload> STREAM_CODEC = StreamCodec.of(SkyboxPayload::write, SkyboxPayload::read);

    public SkyboxPayload {
        if (values.length > MAX_VALUES) throw new IllegalArgumentException("At most " + MAX_VALUES + " values, got " + values.length);
    }

    private static void write(FriendlyByteBuf buf, SkyboxPayload payload) {
        ByteBufCodecs.RESOURCE_LOCATION.encode(buf, payload.skybox);
        buf.writeEnum(payload.action);
        buf.writeVarInt(payload.ticks);
        buf.writeUtf(payload.layer, 256);
        buf.writeVarInt(payload.index);
        buf.writeVarInt(payload.values.length);
        for (float value : payload.values) buf.writeFloat(value);
    }

    private static SkyboxPayload read(FriendlyByteBuf buf) {
        ResourceLocation skybox = ByteBufCodecs.RESOURCE_LOCATION.decode(buf);
        Action action = buf.readEnum(Action.class);
        int ticks = buf.readVarInt();
        String layer = buf.readUtf(256);
        int index = buf.readVarInt();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_VALUES) throw new IllegalArgumentException("Too many values: " + count);
        float[] values = new float[count];
        for (int i = 0; i < count; i++) values[i] = buf.readFloat();
        return new SkyboxPayload(skybox, action, ticks, layer, index, values);
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
        SET_LAYER_VISIBLE,
        SET_LAYER_ALPHA,
        SET_LAYER_PARAM,
        RESET_LAYER,
        /** Flips it: on if it's off, off if it's on (as the client sees it). */
        TOGGLE,
        /** On if the first value isn't 0, off otherwise. */
        SET_ENABLED
    }
}
