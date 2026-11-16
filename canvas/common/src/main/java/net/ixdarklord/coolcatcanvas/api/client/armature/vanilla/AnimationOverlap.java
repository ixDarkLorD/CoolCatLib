package net.ixdarklord.coolcatcanvas.api.client.armature.vanilla;

import net.minecraft.world.entity.LivingEntity;

import java.util.Set;

/**
 * Another mod that animates vanilla models, made known so vanilla-model clips can share parts with it: where its
 * animation overlaps the clips' on a part, the model's {@link OverlapRule} decides which shows. Register one while the client
 * sets up, with {@link VanillaModels#registerOverlap}.
 */
public interface AnimationOverlap {
    /**
     * The parts it is moving on the model posed for this entity, by vanilla part name ({@code "right_arm"}); empty
     * when it moves nothing. Asked once per entity per frame, after vanilla and the other mod have posed the model.
     */
    Set<String> overlappingParts(LivingEntity entity);

    /** Whether it is animating the entity at all, for drivers and conditions ({@link VanillaModels#hasOverlap}). */
    boolean isAnimating(LivingEntity entity);

    /**
     * Whether this draw is one of its own that clips and worn armatures should stay out of, such as a first-person
     * pass drawing the player's arms in front of the camera.
     */
    default boolean skipsDraw(LivingEntity entity) {
        return false;
    }
}
