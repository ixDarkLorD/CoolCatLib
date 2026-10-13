package net.ixdarklord.coolcatcore.internal.platform.fabric;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.ixdarklord.coolcatcore.api.client.registry.MenuScreenRegistry;
import net.ixdarklord.coolcatcore.internal.platform.ClientServices;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Function;
import java.util.function.Supplier;

public final class ClientServicesImpl implements ClientServices {
    private static final ClientServices INSTANCE = new ClientServicesImpl();

    public static ClientServices get() {
        return INSTANCE;
    }

    @Override
    public void registerKeyMapping(KeyMapping mapping) {
        KeyMappingHelper.registerKeyMapping(mapping);
    }

    // Menu types are registered by now on Fabric (the main entrypoint runs first), so the screen goes in straight away.
    @Override
    public <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void registerMenuScreen(Supplier<? extends MenuType<? extends M>> type, MenuScreenRegistry.ScreenFactory<M, U> factory) {
        MenuScreens.register(type.get(), factory::create);
    }

    @Override
    public <T extends TooltipComponent> void registerTooltipComponent(Class<T> type, Function<? super T, ? extends ClientTooltipComponent> factory) {
        ClientTooltipComponentCallback.EVENT.register(data -> type.isInstance(data) ? factory.apply(type.cast(data)) : null);
    }

    // Entity and block entity types are registered by now on Fabric, like menu types. Fabric API makes vanilla's own
    // register methods accessible.
    @Override
    public <T extends Entity> void registerEntityRenderer(Supplier<? extends EntityType<? extends T>> type, EntityRendererProvider<T> provider) {
        EntityRenderers.register(type.get(), provider);
    }

    @Override
    public <T extends BlockEntity, S extends BlockEntityRenderState> void registerBlockEntityRenderer(Supplier<? extends BlockEntityType<? extends T>> type, BlockEntityRendererProvider<T, S> provider) {
        BlockEntityRenderers.register(type.get(), provider);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        FabricClientNetworking.send(payload);
    }
}
