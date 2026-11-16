package net.ixdarklord.coolcatcanvas.internal.mixin.client.armature;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ixdarklord.coolcatcanvas.internal.client.armature.vanilla.VanillaArmatureLayer;
import net.ixdarklord.coolcatcanvas.internal.client.armature.vanilla.VanillaArmatures;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// Vanilla mobs and players with armature clips, changes or attachments (VanillaModels): the attachment layer on every
// living renderer, the animators moved on as the entity starts to be drawn, and the pose applied once vanilla has posed
// the model, before its layers (armor copies it from there).
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Shadow
    @Final
    protected List<RenderLayer<?, ?>> layers;

    @Shadow
    protected EntityModel<?> model;

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "<init>", at = @At("TAIL"))
    private void coolcatlib$addArmatureLayer(EntityRendererProvider.Context context, EntityModel<?> model, float shadow, CallbackInfo ci) {
        this.layers.add(new VanillaArmatureLayer((RenderLayerParent) this));
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    private void coolcatlib$extractArmature(LivingEntity entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        VanillaArmatures.extract((LivingEntityRenderer<?, ?>) (Object) this, entity, partialTicks);
    }

    // Where vanilla scales the model (upside down, at the feet): a clip's root joint moves the whole model from here.
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;scale(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;F)V"))
    private void coolcatlib$moveWholeModel(LivingEntity entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        VanillaArmatures.transformRoot(entity, poseStack);
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V"))
    private void coolcatlib$beforePose(LivingEntity entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        VanillaArmatures.beforePose(this.model);
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", shift = At.Shift.AFTER))
    private void coolcatlib$afterPose(LivingEntity entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        VanillaArmatures.afterPose(this.model, entity);
    }
}
