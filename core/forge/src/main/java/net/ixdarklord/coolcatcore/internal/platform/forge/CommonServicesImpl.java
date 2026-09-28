package net.ixdarklord.coolcatcore.internal.platform.forge;

import io.netty.buffer.Unpooled;
import net.ixdarklord.coolcatcore.api.menu.ExtendedMenus;
import net.ixdarklord.coolcatcore.api.network.PayloadReceiver;
import net.ixdarklord.coolcatcore.api.registry.BlockEntityTypes;
import net.ixdarklord.coolcatcore.api.registry.DeferredRegister;
import net.ixdarklord.coolcatcore.internal.platform.CommonServices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class CommonServicesImpl implements CommonServices {
    private static final CommonServices INSTANCE = new CommonServicesImpl();

    public static CommonServices get() {
        return INSTANCE;
    }

    @Override
    public <T> DeferredRegister<T> createDeferredRegister(String modId, ResourceKey<? extends Registry<T>> registryKey) {
        return new ForgeDeferredRegister<>(modId, registryKey);
    }

    @Override
    public CreativeModeTab.Builder creativeTabBuilder() {
        return CreativeModeTab.builder();
    }

    @Override
    @SuppressWarnings("DataFlowIssue")
    public <T extends BlockEntity> BlockEntityType<T> createBlockEntityType(BlockEntityTypes.Factory<? extends T> factory, Block... blocks) {
        return BlockEntityType.Builder.<T>of(factory::create, blocks).build(null);
    }

    // Forge's menu data is a plain FriendlyByteBuf; the menu's factory reads it with the client's registries.
    @Override
    public <T extends AbstractContainerMenu> MenuType<T> createExtendedMenuType(ExtendedMenus.Factory<T> factory) {
        return IForgeMenuType.create((containerId, inventory, data) -> factory.create(containerId, inventory,
                new RegistryFriendlyByteBuf(data != null ? data : new FriendlyByteBuf(Unpooled.EMPTY_BUFFER), inventory.player.registryAccess())));
    }

    @Override
    public void openExtendedMenu(ServerPlayer player, MenuProvider provider, Consumer<RegistryFriendlyByteBuf> data) {
        player.openMenu(provider, (FriendlyByteBuf buf) -> data.accept(new RegistryFriendlyByteBuf(buf, player.registryAccess())));
    }

    @Override
    public void registerServerReloadListener(ResourceLocation id, PreparableReloadListener listener) {
        ForgeRegistrations.SERVER_RELOAD_LISTENERS.put(id, listener);
    }

    @Override
    public <T extends CustomPacketPayload> void registerServerbound(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PayloadReceiver<T> receiver) {
        ForgeNetworking.registerPlay(type, PacketFlow.SERVERBOUND, codec, receiver);
    }

    @Override
    public <T extends CustomPacketPayload> void registerClientbound(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PayloadReceiver<T> receiver) {
        ForgeNetworking.registerPlay(type, PacketFlow.CLIENTBOUND, codec, receiver);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ForgeNetworking.send(payload, PacketDistributor.PLAYER.with(player));
    }

    @Override
    public <T extends CustomPacketPayload> void registerConfigurationClientbound(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec, ConfigurationReceiver<T> receiver) {
        ForgeNetworking.registerConfigurationClientbound(type, codec, receiver);
    }

    @Override
    public void addConfigurationPayloads(Supplier<List<CustomPacketPayload>> payloads) {
        ForgeRegistrations.CONFIGURATION_PAYLOADS.add(payloads);
    }

    @Override
    public void invalidateBlockHandlers(Level level, BlockPos pos) {
        ForgeTransferCompat.invalidate(level, pos);
    }
}
