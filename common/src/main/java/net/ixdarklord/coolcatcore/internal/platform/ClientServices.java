package net.ixdarklord.coolcatcore.internal.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.architectury.injectables.annotations.ExpectPlatform;
import net.ixdarklord.coolcatcore.api.client.registry.MenuScreenRegistry;
import net.ixdarklord.coolcatcore.api.network.CustomPacketPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
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

    <T extends Entity> void registerEntityRenderer(Supplier<? extends EntityType<? extends T>> type, EntityRendererProvider<T> provider);

    void registerBlockRenderLayer(Supplier<? extends Block> block, RenderType layer);

    /**
     * Builds a block's model as the world's chunks build it (each face shaded by its side, smooth lighting, the light
     * where it stands, its model offset), into the buffer for each chunk layer the loader draws it in.
     */
    void tesselateWorldBlock(BlockAndTintGetter level, BlockState state, BlockPos pos, PoseStack pose, Function<RenderType, VertexConsumer> buffers, RandomSource random);

    void sendToServer(CustomPacketPayload payload);

    /** Bakes models no item or block uses (ExtraModels): those named by the provider, asked at every model load. */
    void registerExtraModels(Function<ResourceManager, Collection<ResourceLocation>> provider);

    /** A model registered with registerExtraModel, once baked; null before. */
    net.minecraft.client.resources.model.@Nullable BakedModel getExtraModel(ResourceLocation model);
}
