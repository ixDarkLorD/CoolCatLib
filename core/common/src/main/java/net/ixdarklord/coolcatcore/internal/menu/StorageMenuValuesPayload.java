package net.ixdarklord.coolcatcore.internal.menu;

import net.ixdarklord.coolcatcore.api.menu.StorageMenu;
import net.ixdarklord.coolcatcore.api.network.Network;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

// Changed synced values of an open StorageMenu, as the bytes their codec wrote.
public record StorageMenuValuesPayload(int containerId, List<Entry> entries) implements CustomPacketPayload {
    public static final Type<StorageMenuValuesPayload> TYPE = new Type<>(CoolCatCore.rl("storage_menu_values"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StorageMenuValuesPayload> STREAM_CODEC = StreamCodec.of(StorageMenuValuesPayload::write, StorageMenuValuesPayload::read);

    public record Entry(String name, byte[] value) {}

    public static void init() {
        Network.registerClientbound(TYPE, STREAM_CODEC, (payload, context) -> context.queue(() -> {
            if (context.getPlayer() != null && context.getPlayer().containerMenu instanceof StorageMenu menu
                    && menu.containerId == payload.containerId()) {
                menu.receiveValues(payload.entries(), context.getPlayer().registryAccess());
            }
        }));
    }

    private static void write(RegistryFriendlyByteBuf buf, StorageMenuValuesPayload payload) {
        buf.writeVarInt(payload.containerId);
        buf.writeVarInt(payload.entries.size());
        for (Entry entry : payload.entries) {
            buf.writeUtf(entry.name);
            buf.writeByteArray(entry.value);
        }
    }

    private static StorageMenuValuesPayload read(RegistryFriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        int size = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) entries.add(new Entry(buf.readUtf(), buf.readByteArray()));
        return new StorageMenuValuesPayload(containerId, entries);
    }

    @Override
    public Type<StorageMenuValuesPayload> type() {
        return TYPE;
    }
}
