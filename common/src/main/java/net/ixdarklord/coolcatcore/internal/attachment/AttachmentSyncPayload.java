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

// Changed Attachment values of one entity or block entity. Values travel as the bytes their key's stream codec wrote, so
// a client missing a key skips just that value.
public record AttachmentSyncPayload(int entityId, @Nullable BlockPos pos, List<Entry> entries) implements CustomPacketPayload {
    public static final Type<AttachmentSyncPayload> TYPE = new Type<>(CoolCatCore.rl("holder_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AttachmentSyncPayload> STREAM_CODEC = StreamCodec.of(AttachmentSyncPayload::write, AttachmentSyncPayload::read);

    public static AttachmentSyncPayload entity(int entityId, List<Entry> entries) {
        return new AttachmentSyncPayload(entityId, null, entries);
    }

    public static AttachmentSyncPayload block(BlockPos pos, List<Entry> entries) {
        return new AttachmentSyncPayload(-1, pos, entries);
    }

    /** {@code value} is {@code null} when the value was reset to its default. */
    public record Entry(ResourceLocation key, byte @Nullable [] value) {}

    private static void write(RegistryFriendlyByteBuf buf, AttachmentSyncPayload payload) {
        buf.writeBoolean(payload.pos != null);
        if (payload.pos != null) BlockPos.STREAM_CODEC.encode(buf, payload.pos);
        else buf.writeVarInt(payload.entityId);
        buf.writeVarInt(payload.entries.size());
        for (Entry entry : payload.entries) {
            ResourceLocation.STREAM_CODEC.encode(buf, entry.key);
            buf.writeBoolean(entry.value != null);
            if (entry.value != null) buf.writeByteArray(entry.value);
        }
    }

    private static AttachmentSyncPayload read(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBoolean() ? BlockPos.STREAM_CODEC.decode(buf) : null;
        int entityId = pos == null ? buf.readVarInt() : -1;
        int size = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ResourceLocation key = ResourceLocation.STREAM_CODEC.decode(buf);
            entries.add(new Entry(key, buf.readBoolean() ? buf.readByteArray() : null));
        }
        return new AttachmentSyncPayload(entityId, pos, entries);
    }

    @Override
    public Type<AttachmentSyncPayload> type() {
        return TYPE;
    }
}
