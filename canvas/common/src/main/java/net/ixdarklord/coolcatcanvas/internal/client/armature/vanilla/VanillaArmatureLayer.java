package net.ixdarklord.coolcatcanvas.internal.client.armature.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureDraw;
import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureFrame;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Draws the armatures a vanilla mob wears ({@code VanillaModel.attach}), each on its part as the part is posed. Added
 * to every living renderer; draws nothing for mobs that wear nothing.
 */
public final class VanillaArmatureLayer extends RenderLayer<LivingEntityRenderState, EntityModel<LivingEntityRenderState>> {
    public VanillaArmatureLayer(RenderLayerParent<LivingEntityRenderState, EntityModel<LivingEntityRenderState>> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, LivingEntityRenderState state, float yRot, float xRot) {
        VanillaState vanilla = ((VanillaStateHolder) state).coolcatlib$vanilla();
        if (vanilla == null || state.isInvisible || VanillaState.skipped(state)) return;
        EntityModel<LivingEntityRenderState> model = this.getParentModel();
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
            Identifier texture = vanilla.textures()[i];
            ArmatureDraw.submit(frame, poseStack, collector, ArmatureDraw.Style.lit(lightCoords)
                    .overlay(LivingEntityRenderer.getOverlayCoords(state, 0.0F))
                    .outline(state.outlineColor)
                    .texture(texture == null ? null : slot -> slot == 0 ? texture : null));
            poseStack.popPose();
        }
    }
}
