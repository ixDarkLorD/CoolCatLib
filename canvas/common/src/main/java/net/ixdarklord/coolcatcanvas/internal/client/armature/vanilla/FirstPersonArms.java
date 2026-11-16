package net.ixdarklord.coolcatcanvas.internal.client.armature.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.ixdarklord.coolcatcanvas.api.client.armature.Animator;
import net.ixdarklord.coolcatcanvas.api.client.armature.vanilla.VanillaModel;
import net.ixdarklord.coolcatcanvas.api.client.armature.vanilla.VanillaModels;
import net.ixdarklord.coolcatcanvas.internal.mixin.client.armature.PlayerRendererInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Your own arms in first person, posed as the player's clips pose them ({@code VanillaModel.firstPersonArms}). Drawn in
 * the hand pass, in place of vanilla's first-person hands: from the camera, the player's own model is laid out as it
 * stands (feet below the eyes, body facing and tilting with where you look), posed exactly as others see it, and only
 * its arms and what they hold are drawn. Being in the hand pass, they have the hands' own depth and near
 * plane, so they never sink into walls or get cut by the camera.
 */
public final class FirstPersonArms {
    // How far the body is drawn behind the eyes, in blocks.
    private static final float BODY_BEHIND = 0.4F;

    private FirstPersonArms() {
    }

    /** Draws the arms if they should be; true when they were, and vanilla's hands should not be. */
    public static boolean draw(float partialTick, PoseStack poseStack, MultiBufferSource buffers, LocalPlayer player, int light) {
        VanillaModel model = VanillaModels.find(EntityType.PLAYER);
        if (model == null || player.isInvisible() || player.isSpectator() || !model.test(player)) return false;
        Animator animator = VanillaArmatures.animator(player);
        if (animator == null || !model.showsFirstPersonArms(animator)) return false;
        EntityRenderer<? super LocalPlayer> found = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
        if (!(found instanceof PlayerRenderer renderer)) return false;

        // The player as others see it this frame (which also moves the animator on: in first person the player is not
        // drawn in the world, so nothing else would), body turned to the camera: no head turn or pitch of its own, as
        // the whole body already turns with the view (an aimed bow would otherwise tilt twice).
        VanillaArmatures.extract(renderer, player, partialTick);
        PlayerModel<AbstractClientPlayer> playerModel = renderer.getModel();
        ((PlayerRendererInvoker) renderer).coolcatcanvas$setModelProperties(player);
        playerModel.attackTime = player.getAttackAnim(partialTick);
        playerModel.riding = player.isPassenger();
        playerModel.young = player.isBaby();
        float speed = Math.min(1.0F, player.walkAnimation.speed(partialTick));
        float position = player.walkAnimation.position(partialTick);
        float bob = player.tickCount + partialTick;
        VanillaArmatures.beforePose(playerModel);
        playerModel.prepareMobModel(player, position, speed, partialTick);
        playerModel.setupAnim(player, position, speed, bob, 0.0F, 0.0F);
        VanillaArmatures.afterPose(playerModel, player);
        ResourceLocation skin = renderer.getTextureLocation(player);

        poseStack.pushPose();
        // From the camera to the body. The body turns with the view (as vanilla's hands do), so an emote's arms stay
        // where they are posed on screen at any pitch: down to the feet, a little behind the eyes so hands brought to
        // the face stay in view rather than in the lens, then into vanilla's upside-down model space.
        poseStack.translate(0.0F, -player.getEyeHeight(), BODY_BEHIND);
        // Turned to face away from the camera, as the body does from the eyes.
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        for (HumanoidArm arm : HumanoidArm.values()) {
            ModelPart armPart = arm == HumanoidArm.RIGHT ? playerModel.rightArm : playerModel.leftArm;
            ModelPart sleeve = arm == HumanoidArm.RIGHT ? playerModel.rightSleeve : playerModel.leftSleeve;
            armPart.render(poseStack, buffers.getBuffer(RenderType.entitySolid(skin)), light, OverlayTexture.NO_OVERLAY);
            sleeve.render(poseStack, buffers.getBuffer(RenderType.entityTranslucent(skin)), light, OverlayTexture.NO_OVERLAY);

            ItemStack item = arm == player.getMainArm() ? player.getMainHandItem() : player.getOffhandItem();
            if (item.isEmpty()) continue;
            // As vanilla's held-item layer places it in a hand (and where a bent forearm took the hand).
            poseStack.pushPose();
            playerModel.translateToHand(arm, poseStack);
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            poseStack.translate((arm == HumanoidArm.LEFT ? -1.0F : 1.0F) / 16.0F, 2.0F / 16.0F, -10.0F / 16.0F);
            ItemDisplayContext display = arm == HumanoidArm.LEFT ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer()
                    .renderItem(player, item, display, arm == HumanoidArm.LEFT, poseStack, buffers, light);
            poseStack.popPose();
        }
        poseStack.popPose();
        return true;
    }
}
