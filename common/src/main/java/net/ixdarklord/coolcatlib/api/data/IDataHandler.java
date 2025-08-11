package net.ixdarklord.coolcatlib.api.data;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public interface IDataHandler<E, T> {
    T get();
    E sendToServer();
    E sendToClient(ServerPlayer player);
    default void clientUpdate() {}

    void saveData(T data);
    E loadData(T data);
    void toNetwork(FriendlyByteBuf buf);
}
