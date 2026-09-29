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
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.network.NetworkHooks;

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

    // Forge hands the client's menu the buffer the server wrote when opening it; a menu opened without one (through
    // MenuType.create) gets an empty buffer.
    @Override
    public <T extends AbstractContainerMenu> MenuType<T> createExtendedMenuType(ExtendedMenus.Factory<T> factory) {
        return IForgeMenuType.create((containerId, inventory, data) ->
                factory.create(containerId, inventory, data != null ? data : new FriendlyByteBuf(Unpooled.EMPTY_BUFFER)));
    }

    @Override
    public void openExtendedMenu(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> data) {
        NetworkHooks.openScreen(player, provider, data);
    }

    @Override
    public void registerServerReloadListener(ResourceLocation id, PreparableReloadListener listener) {
        ForgeRegistrations.SERVER_RELOAD_LISTENERS.put(id, listener);
    }

    @Override
    public <T extends CustomPacketPayload> void registerServerbound(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec, PayloadReceiver<T> receiver) {
        ForgeNetworking.registerServerbound(type, codec, receiver);
    }

    @Override
    public <T extends CustomPacketPayload> void registerClientbound(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec, PayloadReceiver<T> receiver) {
        ForgeNetworking.registerClientbound(type, codec, receiver);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ForgeNetworking.sendToPlayer(player, payload);
    }

    @Override
    public boolean canPlayerReceive(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return ForgeNetworking.canPlayerReceive(player, type);
    }

    // 1.20.1 has no configuration phase: these go out as login packets (see ForgeNetworking).
    @Override
    public <T extends CustomPacketPayload> void registerConfigurationClientbound(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec, ConfigurationReceiver<T> receiver) {
        ForgeNetworking.registerLoginClientbound(type, codec, receiver);
    }

    @Override
    public void addConfigurationPayloads(Supplier<List<CustomPacketPayload>> payloads) {
        ForgeNetworking.addLoginPayloads(payloads);
    }

    // Forge 47's capabilities belong to each block entity: this drops the item handlers CoolCatLib: Core's provider
    // handed out for the one at the position (their holders are told), so the next query finds them again.
    @Override
    public void invalidateBlockHandlers(Level level, BlockPos pos) {
        ForgeTransferCompat.invalidate(level, pos);
    }
}
