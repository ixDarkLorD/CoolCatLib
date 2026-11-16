package net.ixdarklord.coolcatcanvas.internal.mixin.client.armature;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ixdarklord.coolcatcanvas.internal.client.armature.vanilla.BendablePart;
import net.ixdarklord.coolcatcanvas.internal.client.armature.vanilla.LimbBend;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// An item in a bent arm's hand goes where the forearm went.
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Shadow
    protected abstract ModelPart getArm(HumanoidArm side);

    @Inject(method = "translateToHand", at = @At("TAIL"))
    private void coolcatlib$followBentArm(HumanoidArm arm, PoseStack poseStack, CallbackInfo ci) {
        LimbBend.followEnd(BendablePart.of(this.getArm(arm)), poseStack);
    }
}
