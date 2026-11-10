package net.ixdarklord.coolcatcore.internal.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.ixdarklord.coolcatcore.api.client.registry.MenuScreenRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
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

    <T extends BlockEntity> void registerBlockEntityRenderer(Supplier<? extends BlockEntityType<? extends T>> type, BlockEntityRendererProvider<T> provider);

    void registerBlockRenderLayer(Supplier<? extends Block> block, RenderType layer);

    void sendToServer(CustomPacketPayload payload);

    /** Bakes models no item or block uses (ExtraModels): those named by the provider, asked at every model load. */
    void registerExtraModels(Function<ResourceManager, Collection<ResourceLocation>> provider);

    /** A model registered with registerExtraModel, once baked; null before. */
    net.minecraft.client.resources.model.@Nullable BakedModel getExtraModel(ResourceLocation model);
}
