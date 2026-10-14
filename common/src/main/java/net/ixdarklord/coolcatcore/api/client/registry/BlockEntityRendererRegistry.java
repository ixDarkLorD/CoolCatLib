package net.ixdarklord.coolcatcore.api.client.registry;

import net.ixdarklord.coolcatcore.internal.platform.ClientServices;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Supplier;

/**
 * The renderers block entities are drawn with. Register while the client initialises.
 * <pre>{@code
 * BlockEntityRendererRegistry.register(MyBlockEntities.PEDESTAL, PedestalRenderer::new);
 * }</pre>
 */
public final class BlockEntityRendererRegistry {
    private BlockEntityRendererRegistry() {}

    /**
     * @param type the block entity type, supplied so it can be a {@code RegistryEntry} not yet registered
     */
    public static <T extends BlockEntity, S extends BlockEntityRenderState> void register(Supplier<? extends BlockEntityType<? extends T>> type, BlockEntityRendererProvider<T, S> provider) {
        ClientServices.get().registerBlockEntityRenderer(type, provider);
    }
}
