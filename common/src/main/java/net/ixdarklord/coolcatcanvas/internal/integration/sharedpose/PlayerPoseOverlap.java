package net.ixdarklord.coolcatcanvas.internal.integration.sharedpose;

import com.zigythebird.playeranim.accessors.IAnimatedAvatar;
import com.zigythebird.playeranim.accessors.IAvatarAnimationState;
import com.zigythebird.playeranim.animation.AvatarAnimManager;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.ixdarklord.coolcatcanvas.api.client.armature.vanilla.AnimationOverlap;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashSet;
import java.util.Set;

/**
 * The player animation mod Better Combat animates players with. It poses the player's parts inside vanilla's own
 * posing, so CoolCatLib's clips come after it; this tells them which parts it is moving, so they can step aside, and
 * keeps them out of its first-person pass. Loaded only when that mod is (VanillaArmatures).
 */
public final class PlayerPoseOverlap implements AnimationOverlap {
    public static final String MOD_ID = "player_animation_library";
    // A value no animation sets: a channel the other mod touched no longer holds it.
    private static final float UNSET = 123456.0F;
    // Its bones and the vanilla parts they move ("torso" is the body part; its "body" moves the whole model).
    private static final String[][] BONES = {
            {"head", "head"}, {"torso", "body"}, {"right_arm", "right_arm"}, {"left_arm", "left_arm"},
            {"right_leg", "right_leg"}, {"left_leg", "left_leg"}};

    @Override
    public Set<String> overlappingParts(LivingEntityRenderState state) {
        AvatarAnimManager manager = manager(state);
        if (manager == null || !manager.isActive()) return Set.of();
        Set<String> parts = new HashSet<>();
        for (String[] bone : BONES) {
            PlayerAnimBone probe = new PlayerAnimBone(bone[0]);
            probe.position.set(UNSET);
            probe.rotation.set(UNSET);
            probe.scale.set(UNSET);
            manager.get3DTransform(probe);
            if (changed(probe)) parts.add(bone[1]);
        }
        return parts;
    }

    @Override
    public boolean isAnimating(LivingEntity entity) {
        return entity instanceof IAnimatedAvatar avatar && avatar.playerAnimLib$getAnimManager() != null && avatar.playerAnimLib$getAnimManager().isActive();
    }

    @Override
    public boolean skipsDraw(LivingEntityRenderState state) {
        return state instanceof IAvatarAnimationState avatar && avatar.playerAnimLib$isFirstPersonPass();
    }

    private static AvatarAnimManager manager(LivingEntityRenderState state) {
        return state instanceof IAvatarAnimationState avatar ? avatar.playerAnimLib$getAnimManager() : null;
    }

    private static boolean changed(PlayerAnimBone bone) {
        return bone.position.x != UNSET || bone.position.y != UNSET || bone.position.z != UNSET
                || bone.rotation.x != UNSET || bone.rotation.y != UNSET || bone.rotation.z != UNSET
                || bone.scale.x != UNSET || bone.scale.y != UNSET || bone.scale.z != UNSET;
    }
}
