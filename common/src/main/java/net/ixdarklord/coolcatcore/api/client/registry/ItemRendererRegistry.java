package net.ixdarklord.coolcatcore.api.client.registry;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Draws items in code, the same on every loader: an item whose model is {@code "parent": "builtin/entity"} is drawn by
 * its renderer wherever it shows (in hands, slots, frames, on the ground), with the model's display transforms already
 * applied. Register while the client initialises.
 * <pre>{@code
 * ItemRendererRegistry.register(MyItems.ORB, (stack, context, pose, buffers, light, overlay) -> ...);
 * }</pre>
 */
public final class ItemRendererRegistry {
    private static final Map<Supplier<? extends ItemLike>, Renderer> PENDING = new ConcurrentHashMap<>();
    private static final Map<Item, Renderer> RENDERERS = new ConcurrentHashMap<>();

    private ItemRendererRegistry() {}

    public static void register(Supplier<? extends ItemLike> item, Renderer renderer) {
        PENDING.put(item, renderer);
    }

    /** The renderer of an item, if one was registered. */
    public static @Nullable Renderer get(Item item) {
        if (!PENDING.isEmpty()) {
            PENDING.forEach((supplier, renderer) -> RENDERERS.put(supplier.get().asItem(), renderer));
            PENDING.clear();
        }
        return RENDERERS.get(item);
    }

    @FunctionalInterface
    public interface Renderer {
        void render(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay);
    }
}
