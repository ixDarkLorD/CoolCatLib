package net.ixdarklord.coolcatcanvas.internal.mixin.client.armature;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ixdarklord.coolcatcanvas.internal.client.armature.vanilla.FirstPersonArms;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// While an emote shows your arms in first person (VanillaModel.firstPersonArms), they are drawn in place of the hands.
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void coolcatlib$posedArms(float partialTick, PoseStack poseStack, MultiBufferSource.BufferSource buffers, LocalPlayer player, int light, CallbackInfo ci) {
        if (FirstPersonArms.draw(partialTick, poseStack, buffers, player, light)) ci.cancel();
    }
}
