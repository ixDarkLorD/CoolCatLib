package net.ixdarklord.coolcatcanvas.internal.mixin.client.armature;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ixdarklord.coolcatcanvas.internal.client.armature.render.ArmatureItemRenderer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Armature items: every loader hands "builtin/entity" items it has no renderer of its own for to this one, and those
// whose model names an armature are drawn with it (ArmatureItemRenderer).
@Mixin(BlockEntityWithoutLevelRenderer.class)
public abstract class BlockEntityWithoutLevelRendererMixin {
    @Inject(method = "renderByItem", at = @At("HEAD"), cancellable = true)
    private void coolcatcanvas$armatureItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay, CallbackInfo ci) {
        if (ArmatureItemRenderer.render(stack, pose, buffers, light, overlay)) ci.cancel();
    }
}
