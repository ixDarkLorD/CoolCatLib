package net.ixdarklord.coolcatcanvas.api.client.armature.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureFrame;
import net.minecraft.client.renderer.SubmitNodeCollector;

/**
 * Something drawn at a point of an armature, moving with it: a lantern on a hook, a flame on a torch joint, a block on a
 * cart. The pose stack is at the point, in blocks, turned as the point is.
 * <pre>{@code
 * renderer.attach("lantern_hook", (frame, poseStack, collector, light) -> drawLantern(poseStack, collector, light));
 * }</pre>
 */
@FunctionalInterface
public interface ArmatureAttachment {
    void submit(ArmatureFrame frame, PoseStack poseStack, SubmitNodeCollector collector, int light);
}
