package net.ixdarklord.coolcatcanvas.internal.client.armature.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureDraw;
import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureFrame;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * Draws the armatures a vanilla mob wears ({@code VanillaModel.attach}), each on its part as the part is posed. Added
 * to every living renderer; draws nothing for mobs that wear nothing.
 */
public final class VanillaArmatureLayer extends RenderLayer<LivingEntity, EntityModel<LivingEntity>> {
    public VanillaArmatureLayer(RenderLayerParent<LivingEntity, EntityModel<LivingEntity>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int lightCoords, LivingEntity entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        VanillaState vanilla = ((VanillaStateHolder) entity).coolcatlib$vanilla();
        if (vanilla == null || entity.isInvisible() || VanillaState.skipped(entity)) return;
        EntityModel<LivingEntity> model = this.getParentModel();
        for (int i = 0; i < vanilla.frames().length; i++) {
            ArmatureFrame frame = vanilla.frame(i);
            if (frame == null) continue;
            List<ModelPart> path = VanillaArmatures.path(model, vanilla.attachment(i).part());
            if (path == null || !path.stream().allMatch(part -> part.visible)) continue;
            poseStack.pushPose();
            for (ModelPart part : path) part.translateAndRotate(poseStack);
            // From the part's vanilla axes (upside down, mirrored) back to Blockbench's.
            poseStack.scale(-1.0F, -1.0F, 1.0F);
            vanilla.attachment(i).transform(poseStack);
            ResourceLocation texture = vanilla.textures()[i];
            ArmatureDraw.submit(frame, poseStack, buffers, ArmatureDraw.Style.lit(lightCoords)
                    .overlay(LivingEntityRenderer.getOverlayCoords(entity, 0.0F))
                    .texture(texture == null ? null : slot -> slot == 0 ? texture : null));
            poseStack.popPose();
        }
    }
}
