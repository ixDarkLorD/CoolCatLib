package net.ixdarklord.coolcatcore.internal.platform.fabric;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.ixdarklord.coolcatcore.api.menu.ExtendedMenus;
import net.ixdarklord.coolcatcore.api.network.PacketContext;
import net.ixdarklord.coolcatcore.api.network.PayloadReceiver;
import net.ixdarklord.coolcatcore.api.platform.Env;
import net.ixdarklord.coolcatcore.api.platform.Platform;
import net.ixdarklord.coolcatcore.api.registry.BlockEntityTypes;
import net.ixdarklord.coolcatcore.api.registry.DeferredRegister;
import net.ixdarklord.coolcatcore.internal.platform.CommonServices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class CommonServicesImpl implements CommonServices {
    private static final CommonServices INSTANCE = new CommonServicesImpl();
    private static final List<Supplier<List<CustomPacketPayload>>> CONFIGURATION_PAYLOADS = new CopyOnWriteArrayList<>();
    private static boolean configurationHooked;

    public static CommonServices get() {
        return INSTANCE;
    }

    @Override
    public <T> DeferredRegister<T> createDeferredRegister(String modId, ResourceKey<? extends Registry<T>> registryKey) {
        return new FabricDeferredRegister<>(modId, registryKey);
    }

    @Override
    public CreativeModeTab.Builder creativeTabBuilder() {
        return FabricCreativeModeTab.builder();
    }

    @Override
    public <T extends BlockEntity> BlockEntityType<T> createBlockEntityType(BlockEntityTypes.Factory<? extends T> factory, Block... blocks) {
        return FabricBlockEntityTypeBuilder.<T>create(factory::create, blocks).build();
    }

    // Fabric's extended menus carry typed data; the menu's data travels as the bytes a RegistryFriendlyByteBuf wrote.
    @Override
    public <T extends AbstractContainerMenu> MenuType<T> createExtendedMenuType(ExtendedMenus.Factory<T> factory) {
        return new ExtendedMenuType<>((int containerId, Inventory inventory, byte[] data) -> {
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(data), inventory.player.registryAccess());
            try {
                return factory.create(containerId, inventory, buf);
            } finally {
                buf.release();
            }
        }, ByteBufCodecs.BYTE_ARRAY);
    }

    @Override
    public void openExtendedMenu(ServerPlayer player, MenuProvider provider, Consumer<RegistryFriendlyByteBuf> data) {
        player.openMenu(new ExtendedMenuProvider<byte[]>() {
            @Override
            public byte[] getScreenOpeningData(ServerPlayer target) {
                RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), target.registryAccess());
                try {
                    data.accept(buf);
                    byte[] bytes = new byte[buf.readableBytes()];
                    buf.readBytes(bytes);
                    return bytes;
                } finally {
                    buf.release();
                }
            }

            @Override
            public Component getDisplayName() {
                return provider.getDisplayName();
            }

            @Override
            public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player menuPlayer) {
                return provider.createMenu(containerId, inventory, menuPlayer);
            }

            @Override
            public boolean shouldCloseCurrentScreen() {
                return provider.shouldCloseCurrentScreen();
            }
        });
    }

    @Override
    public void registerServerReloadListener(Identifier id, PreparableReloadListener listener) {
        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(id, listener);
    }

    @Override
    public <T extends CustomPacketPayload> void registerServerbound(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PayloadReceiver<T> receiver) {
        PayloadTypeRegistry.serverboundPlay().register(type, codec);
        ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> receiver.receive(payload, new PacketContext() {
            @Override
            public Player getPlayer() {
                return context.player();
            }

            @Override
            public void queue(Runnable task) {
                context.server().execute(task);
            }

            @Override
            public Env getEnvironment() {
                return Env.SERVER;
            }
        }));
    }

    @Override
    public <T extends CustomPacketPayload> void registerClientbound(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, PayloadReceiver<T> receiver) {
        PayloadTypeRegistry.clientboundPlay().register(type, codec);
        if (Platform.isClient()) FabricClientNetworking.registerReceiver(type, receiver);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public <T extends CustomPacketPayload> void registerConfigurationClientbound(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec, ConfigurationReceiver<T> receiver) {
        PayloadTypeRegistry.clientboundConfiguration().register(type, codec);
        if (Platform.isClient()) FabricClientNetworking.registerConfigurationReceiver(type, receiver);
    }

    // Sent directly as configuration starts, ahead of the configuration tasks (registry sync among them).
    @Override
    public synchronized void addConfigurationPayloads(Supplier<List<CustomPacketPayload>> payloads) {
        CONFIGURATION_PAYLOADS.add(payloads);
        if (configurationHooked) return;
        configurationHooked = true;
        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
            for (Supplier<List<CustomPacketPayload>> supplier : CONFIGURATION_PAYLOADS) {
                for (CustomPacketPayload payload : supplier.get()) {
                    if (ServerConfigurationNetworking.canSend(handler, payload.type())) ServerConfigurationNetworking.send(handler, payload);
                }
            }
        });
    }

    // Fabric's block API caches notice block entity changes on their own.
    @Override
    public void invalidateBlockHandlers(Level level, BlockPos pos) {
    }
}
