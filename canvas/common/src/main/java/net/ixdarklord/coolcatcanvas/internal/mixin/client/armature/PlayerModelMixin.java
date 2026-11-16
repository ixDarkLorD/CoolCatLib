package net.ixdarklord.coolcatcanvas.internal.mixin.client.armature;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ixdarklord.coolcatcanvas.internal.client.armature.vanilla.BendablePart;
import net.ixdarklord.coolcatcanvas.internal.client.armature.vanilla.LimbBend;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The player's own way to its hands (slim arms): an item in a bent arm's hand goes where the forearm went.
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    @Inject(method = "translateToHand", at = @At("TAIL"))
    private void coolcatlib$followBentArm(HumanoidArm arm, PoseStack poseStack, CallbackInfo ci) {
        PlayerModel<?> model = (PlayerModel<?>) (Object) this;
        LimbBend.followEnd(BendablePart.of(arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm), poseStack);
    }
}
