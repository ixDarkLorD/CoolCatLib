package net.ixdarklord.coolcatcore.api.client.registry;

import net.ixdarklord.coolcatcore.internal.platform.ClientServices;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

/**
 * The layer a block is drawn in with the world's chunks: {@link RenderType#solid()} (the default), {@link RenderType#cutout()}
 * or {@link RenderType#cutoutMipped()} for textures with clear pixels, {@link RenderType#translucent()} for partly
 * see-through ones. Register while the client initialises.
 * <pre>{@code
 * BlockRenderLayers.register(MyBlocks.GLASS_CASE, RenderType.translucent());
 * }</pre>
 */
public final class BlockRenderLayers {
    private BlockRenderLayers() {}

    /**
     * @param block the block, supplied so it can be a {@code RegistryEntry} not yet registered
     */
    public static void register(Supplier<? extends Block> block, RenderType layer) {
        ClientServices.get().registerBlockRenderLayer(block, layer);
    }
}
