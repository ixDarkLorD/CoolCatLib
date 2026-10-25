package net.ixdarklord.coolcatcore.internal.attachment;

import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// Changed Attachment values of one entity, block entity, the player's level, or the server. Values travel as the bytes
// their key's stream codec wrote, so a client missing a key skips just that value.
public record AttachmentSyncPayload(Target target, int entityId, @Nullable BlockPos pos, List<Entry> entries) implements CustomPacketPayload {
    public static final Type<AttachmentSyncPayload> TYPE = new Type<>(CoolCatCore.rl("holder_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AttachmentSyncPayload> STREAM_CODEC = StreamCodec.of(AttachmentSyncPayload::write, AttachmentSyncPayload::read);

    public enum Target { ENTITY, BLOCK, LEVEL, SERVER }

    public static AttachmentSyncPayload entity(int entityId, List<Entry> entries) {
        return new AttachmentSyncPayload(Target.ENTITY, entityId, null, entries);
    }

    public static AttachmentSyncPayload block(BlockPos pos, List<Entry> entries) {
        return new AttachmentSyncPayload(Target.BLOCK, -1, pos, entries);
    }

    /** The level the player is in. */
    public static AttachmentSyncPayload level(List<Entry> entries) {
        return new AttachmentSyncPayload(Target.LEVEL, -1, null, entries);
    }

    public static AttachmentSyncPayload server(List<Entry> entries) {
        return new AttachmentSyncPayload(Target.SERVER, -1, null, entries);
    }

    /** {@code value} is {@code null} when the value was reset to its default. */
    public record Entry(ResourceLocation key, byte @Nullable [] value) {}

    private static void write(RegistryFriendlyByteBuf buf, AttachmentSyncPayload payload) {
        buf.writeEnum(payload.target);
        if (payload.target == Target.BLOCK) BlockPos.STREAM_CODEC.encode(buf, payload.pos);
        else if (payload.target == Target.ENTITY) buf.writeVarInt(payload.entityId);
        buf.writeVarInt(payload.entries.size());
        for (Entry entry : payload.entries) {
            ResourceLocation.STREAM_CODEC.encode(buf, entry.key);
            buf.writeBoolean(entry.value != null);
            if (entry.value != null) buf.writeByteArray(entry.value);
        }
    }

    private static AttachmentSyncPayload read(RegistryFriendlyByteBuf buf) {
        Target target = buf.readEnum(Target.class);
        BlockPos pos = target == Target.BLOCK ? BlockPos.STREAM_CODEC.decode(buf) : null;
        int entityId = target == Target.ENTITY ? buf.readVarInt() : -1;
        int size = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ResourceLocation key = ResourceLocation.STREAM_CODEC.decode(buf);
            entries.add(new Entry(key, buf.readBoolean() ? buf.readByteArray() : null));
        }
        return new AttachmentSyncPayload(target, entityId, pos, entries);
    }

    @Override
    public Type<AttachmentSyncPayload> type() {
        return TYPE;
    }
}
