package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ixdarklord.coolcatcanvas.internal.client.armature.vanilla.BendablePart;
import net.ixdarklord.coolcatcanvas.internal.client.armature.vanilla.LimbBend;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The player's own way to its hands (slim arms): an item in a bent arm's hand goes where the forearm went.
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    @Inject(method = "translateToHand(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("TAIL"))
    private void coolcatlib$followBentArm(AvatarRenderState state, HumanoidArm arm, PoseStack poseStack, CallbackInfo ci) {
        LimbBend.followEnd(BendablePart.of(((PlayerModel) (Object) this).getArm(arm)), poseStack);
    }
}
