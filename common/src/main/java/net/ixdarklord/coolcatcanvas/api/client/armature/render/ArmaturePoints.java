package net.ixdarklord.coolcatcanvas.api.client.armature.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ixdarklord.coolcatcanvas.api.client.armature.BakedArmature;
import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureDraw;
import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureFrame;
import org.joml.Matrix4f;

/** Moving a pose stack to an armature's points, for attachments and sockets. */
public final class ArmaturePoints {
    private ArmaturePoints() {
    }

    /**
     * Moves the pose stack (at the model's origin, as {@link ArmatureDraw#submit} takes it) to a point or joint pivot, in
     * blocks, turned as the point is and scaled with the armature. Returns false, leaving the stack as it was, if the armature
     * has no point or joint by that name.
     */
    public static boolean moveTo(ArmatureFrame frame, String point, PoseStack poseStack) {
        Matrix4f matrix = frame.pointMatrix(point);
        if (matrix == null) return false;
        BakedArmature armature = frame.armature();
        poseStack.mulPose(ArmatureDraw.modelTransform(armature));
        poseStack.mulPose(matrix);
        poseStack.scale(16.0F, 16.0F, 16.0F);
        return true;
    }
}
