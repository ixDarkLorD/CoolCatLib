package net.ixdarklord.coolcatcanvas.internal.bossbar;

import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Server to client: the style a boss bar is drawn in ({@code CustomBossBars}), and the entity it belongs to (-1 for
 * none). A null style puts it back to the game's own bar.
 */
public record BossBarStylePayload(UUID bar, @Nullable Identifier style, int entityId) implements CustomPacketPayload {
    public static final Type<BossBarStylePayload> TYPE = new Type<>(CoolCatCanvas.rl("boss_bar_style"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BossBarStylePayload> STREAM_CODEC = StreamCodec.of(BossBarStylePayload::write, BossBarStylePayload::read);

    private static void write(RegistryFriendlyByteBuf buf, BossBarStylePayload payload) {
        buf.writeUUID(payload.bar);
        buf.writeBoolean(payload.style != null);
        if (payload.style != null) buf.writeIdentifier(payload.style);
        buf.writeVarInt(payload.entityId + 1);
    }

    private static BossBarStylePayload read(RegistryFriendlyByteBuf buf) {
        UUID bar = buf.readUUID();
        Identifier style = buf.readBoolean() ? buf.readIdentifier() : null;
        return new BossBarStylePayload(bar, style, buf.readVarInt() - 1);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
