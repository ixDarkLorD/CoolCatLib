package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.ixdarklord.coolcatcanvas.internal.client.armature.vanilla.VanillaArmatures;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Every model vanilla draws for an entity (the body, its armor, ...) is posed here just before drawing; a vanilla mob's
// armature clips and changes go on after vanilla's own pose, by part name, so armor follows them.
@Mixin(ModelFeatureRenderer.class)
public abstract class ModelFeatureRendererMixin {
    @Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V"))
    private void coolcatlib$beforePose(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
        VanillaArmatures.beforePose(submit.model());
    }

    @Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V", shift = At.Shift.AFTER))
    private void coolcatlib$afterPose(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
        VanillaArmatures.afterPose(submit.model(), submit.state());
    }
}
