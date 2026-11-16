package net.ixdarklord.coolcatcanvas.api.client.armature;

/**
 * Code that moves joints each frame, after the animation layers: procedural motion the clips can't know about.
 * <pre>{@code
 * animator.modify((pose, ctx) -> pose.rotate("propeller", 0, (float) ctx.time() * 720, 0));
 * animator.modify((pose, ctx) -> { if (!ctx.<Wisp>owner().hasSaddle()) pose.hide("saddle"); });
 * }</pre>
 */
@FunctionalInterface
public interface JointModifier {
    void modify(ArmaturePose pose, ArmatureContext context);
}
