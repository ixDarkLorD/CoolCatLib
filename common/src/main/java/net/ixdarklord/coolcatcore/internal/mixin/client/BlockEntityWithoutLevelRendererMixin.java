package net.ixdarklord.coolcatcore.internal.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ixdarklord.coolcatcore.api.client.registry.ItemRendererRegistry;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// ItemRendererRegistry: every loader hands "builtin/entity" items it has no renderer of its own for to this one.
@Mixin(BlockEntityWithoutLevelRenderer.class)
public abstract class BlockEntityWithoutLevelRendererMixin {
    @Inject(method = "renderByItem", at = @At("HEAD"), cancellable = true)
    private void coolcatcore$customItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay, CallbackInfo ci) {
        ItemRendererRegistry.Renderer renderer = ItemRendererRegistry.get(stack.getItem());
        if (renderer == null) return;
        renderer.render(stack, context, pose, buffers, light, overlay);
        ci.cancel();
    }
}
