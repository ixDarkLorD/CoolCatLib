package net.ixdarklord.coolcatcanvas.api.client.armature.vanilla;

import net.ixdarklord.coolcatcanvas.api.client.armature.ArmatureContext;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.jetbrains.annotations.Nullable;

/**
 * A vanilla model being posed, after vanilla's own animation and any armature clips: change its parts by name
 * ({@code "head"}, {@code "right_arm"}, {@code "left_leg"}, {@code "body"}; any case or {@code rightArm} spelling).
 * Values are in the units of a Blockbench keyframe: degrees and pixels, Y up. A name the model lacks is ignored. The
 * changes last for this one draw, and armor and held items follow them.
 * <pre>{@code
 * VanillaModels.player().pose((pose, ctx) -> {
 *     if (ctx.living().isCrouching()) pose.scale("head", 1.3F);
 *     pose.rotate("right_arm", 0, 0, 10);
 * });
 * }</pre>
 */
public interface VanillaPose {
    /** The vanilla part, for anything not covered here; null if the model has no such part. */
    @Nullable ModelPart part(String name);

    /** Turns a part further, in degrees. */
    VanillaPose rotate(String part, float x, float y, float z);

    /** Replaces vanilla's turn of a part (from its rest), in degrees. */
    VanillaPose setRotation(String part, float x, float y, float z);

    /** Moves a part (and what hangs from it), in pixels. */
    VanillaPose translate(String part, float x, float y, float z);

    /** Scales a part (and what hangs from it). */
    VanillaPose scale(String part, float x, float y, float z);

    default VanillaPose scale(String part, float scale) {
        return this.scale(part, scale, scale, scale);
    }

    /**
     * Bends a part along its length, at its middle: an elbow, a knee. Its sleeve or pants layer, its armor and an item
     * in its hand bend with it.
     *
     * @param degrees   how far; positive bends toward {@code direction}
     * @param direction which way round the limb, in degrees: 0 forward, 90 to its left, 180 back
     */
    VanillaPose bend(String part, float degrees, float direction);

    default VanillaPose bend(String part, float degrees) {
        return this.bend(part, degrees, 0.0F);
    }

    /** Hides a part and what hangs from it. */
    VanillaPose hide(String part);

    /** What is being drawn. */
    LivingEntityRenderState state();

    /** Changes made to a vanilla model every time it is posed. */
    @FunctionalInterface
    interface Modifier {
        void modify(VanillaPose pose, ArmatureContext context);
    }
}
