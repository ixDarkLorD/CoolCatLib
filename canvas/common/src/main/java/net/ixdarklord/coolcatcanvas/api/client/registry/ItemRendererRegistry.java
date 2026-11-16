package net.ixdarklord.coolcatcanvas.api.client.registry;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.function.Supplier;

/**
 * Items drawn by code, the same on every loader: a chest's lid, a shield's pattern, an icon floating over a block. An
 * item whose model is {@code "parent": "builtin/entity"} is drawn by its renderer wherever it shows (in hands, slots,
 * frames, on the ground), with the model's display transforms already applied. Register while the client initialises.
 * <pre>{@code
 * ItemRendererRegistry.register(MyItems.TROPHY, (stack, context, poseStack, buffers, light, overlay) -> ...);
 * }</pre>
 */
public final class ItemRendererRegistry {
    private ItemRendererRegistry() {}

    /**
     * @param item the item, supplied so it can be a {@code RegistryEntry} not yet registered
     */
    @SuppressWarnings("deprecation")
    public static void register(Supplier<? extends ItemLike> item, Renderer renderer) {
        net.ixdarklord.coolcatcore.api.client.registry.ItemRendererRegistry.register(item, renderer::render);
    }

    /** Draws an item, in the pose its model's display transforms put it in. */
    @FunctionalInterface
    public interface Renderer {
        void render(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay);
    }
}
