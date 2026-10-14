package net.ixdarklord.coolcatcore.internal.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.ixdarklord.coolcatcore.api.client.registry.MenuScreenRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
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

// The client-only half of the loader services; only ever loaded on a client.
public interface ClientServices {
    @ExpectPlatform
    static ClientServices get() {
        throw new UnsupportedOperationException("This method has not been implemented in the loader.");
    }

    void registerKeyMapping(KeyMapping mapping);

    <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void registerMenuScreen(Supplier<? extends MenuType<? extends M>> type, MenuScreenRegistry.ScreenFactory<M, U> factory);

    <T extends TooltipComponent> void registerTooltipComponent(Class<T> type, Function<? super T, ? extends ClientTooltipComponent> factory);

    <T extends Entity> void registerEntityRenderer(Supplier<? extends EntityType<? extends T>> type, EntityRendererProvider<T> provider);

    <T extends BlockEntity, S extends BlockEntityRenderState> void registerBlockEntityRenderer(Supplier<? extends BlockEntityType<? extends T>> type, BlockEntityRendererProvider<T, S> provider);

    void sendToServer(CustomPacketPayload payload);
}
