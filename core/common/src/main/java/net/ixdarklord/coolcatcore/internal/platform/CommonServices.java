package net.ixdarklord.coolcatcore.internal.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.ixdarklord.coolcatcore.api.menu.ExtendedMenus;
import net.ixdarklord.coolcatcore.api.network.PayloadReceiver;
import net.ixdarklord.coolcatcore.api.registry.BlockEntityTypes;
import net.ixdarklord.coolcatcore.api.registry.DeferredRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.ixdarklord.coolcatcore.api.network.codec.StreamCodec;
import net.ixdarklord.coolcatcore.api.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

// What each loader implements for the common API (registration, menus, reload listeners, networking).
public interface CommonServices {
    @ExpectPlatform
    static CommonServices get() {
        throw new UnsupportedOperationException("This method has not been implemented in the loader.");
    }

    <T> DeferredRegister<T> createDeferredRegister(String modId, ResourceKey<? extends Registry<T>> registryKey);

    CreativeModeTab.Builder creativeTabBuilder();

    <T extends BlockEntity> BlockEntityType<T> createBlockEntityType(BlockEntityTypes.Factory<? extends T> factory, Block... blocks);

    <T extends AbstractContainerMenu> MenuType<T> createExtendedMenuType(ExtendedMenus.Factory<T> factory);

    void openExtendedMenu(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> data);

    void registerServerReloadListener(ResourceLocation id, PreparableReloadListener listener);

    <T extends CustomPacketPayload> void registerServerbound(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec, PayloadReceiver<T> receiver);

    <T extends CustomPacketPayload> void registerClientbound(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec, PayloadReceiver<T> receiver);

    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    /**
     * A payload the server sends while a client is still logging in (before it joins the world), received on the
     * client's main thread; the login waits until the receiver has run. 1.20.1 has no configuration phase (1.20.2+),
     * so these travel as login queries. The receiver only runs on clients.
     */
    <T extends CustomPacketPayload> void registerConfigurationClientbound(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec, ConfigurationReceiver<T> receiver);

    /** Payloads to send to every client as its login starts; asked for once per connection. */
    void addConfigurationPayloads(Supplier<List<CustomPacketPayload>> payloads);

    /** Invalidates the loader's own handler caches at a position (Forge's capabilities); a no-op elsewhere. */
    void invalidateBlockHandlers(Level level, BlockPos pos);

    @FunctionalInterface
    interface ConfigurationReceiver<T> {
        /** @param disconnect ends the connection with a reason shown to the player */
        void receive(T payload, Consumer<Component> disconnect);
    }
}
