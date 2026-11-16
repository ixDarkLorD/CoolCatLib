package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.ixdarklord.coolcatcanvas.internal.client.armature.vanilla.VanillaArmatures;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Every model vanilla draws for an entity (the body, its armor, ...) is posed here just before drawing; a vanilla mob's
// armature clips and changes go on after vanilla's own pose, by part name, so armor follows them.
@Mixin(ModelFeatureRenderer.class)
public abstract class ModelFeatureRendererMixin {
    @Inject(method = "renderModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V"))
    private void coolcatlib$beforePose(SubmitNodeStorage.ModelSubmit<?> submit, RenderType renderType, VertexConsumer buffer, OutlineBufferSource outline,
                                       MultiBufferSource.BufferSource crumbling, CallbackInfo ci) {
        VanillaArmatures.beforePose(submit.model());
    }

    @Inject(method = "renderModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V", shift = At.Shift.AFTER))
    private void coolcatlib$afterPose(SubmitNodeStorage.ModelSubmit<?> submit, RenderType renderType, VertexConsumer buffer, OutlineBufferSource outline,
                                      MultiBufferSource.BufferSource crumbling, CallbackInfo ci) {
        VanillaArmatures.afterPose(submit.model(), submit.state());
    }
}
