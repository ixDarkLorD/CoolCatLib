package net.ixdarklord.coolcatcanvas.api.client.armature;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import java.util.function.Function;

/**
 * A joint that turns to look at something. A living entity's joints follow where it looks; anything else (a turret
 * block, a statue's head, a held item) follows the nearest player in range unless given a target. Several joints can
 * share the turn: {@code lookAt("neck").weight(0.4F)} and {@code lookAt("head").weight(0.6F)}.
 * <pre>{@code
 * animator.lookAt("head").maxYaw(70).maxPitch(40);
 * animator.lookAt("turret").range(16).smoothing(0.25F);
 * animator.lookAt("eye").target(ctx -> someWorldPosition);
 * }</pre>
 */
public final class LookAt {
    private final String joint;
    private float weight = 1.0F;
    private float maxYaw = 75.0F;
    private float maxPitch = 45.0F;
    private float smoothing;
    private double range = 8.0;
    private @Nullable Function<ArmatureContext, @Nullable Vec3> target;

    private float yaw;
    private float pitch;
    private boolean started;

    LookAt(String joint) {
        this.joint = joint;
    }

    public String joint() {
        return this.joint;
    }

    /** How much of the turn this joint takes, 0 to 1. */
    public LookAt weight(float weight) {
        this.weight = Mth.clamp(weight, 0.0F, 1.0F);
        return this;
    }

    /** The furthest it turns left or right, in degrees. */
    public LookAt maxYaw(float degrees) {
        this.maxYaw = Math.max(0.0F, degrees);
        return this;
    }

    /** The furthest it tilts up or down, in degrees. */
    public LookAt maxPitch(float degrees) {
        this.maxPitch = Math.max(0.0F, degrees);
        return this;
    }

    /** Seconds it takes to turn most of the way to a new direction; 0 snaps. Living entities' heads are already smooth. */
    public LookAt smoothing(float seconds) {
        this.smoothing = Math.max(0.0F, seconds);
        return this;
    }

    /** How near a player must be to be looked at, for things without a head of their own to follow. */
    public LookAt range(double blocks) {
        this.range = blocks;
        return this;
    }

    /** What to look at, in the world; null from the function looks ahead. */
    public LookAt target(Function<ArmatureContext, @Nullable Vec3> target) {
        this.target = target;
        return this;
    }

    // Adds the turn to the pose. modelToWorld places the model, for targets given as world positions.
    void apply(ArmaturePose pose, ArmatureContext context, Matrix4fc modelToWorld, float deltaTime) {
        int index = pose.armature.jointIndex(this.joint);
        if (index < 0) return;
        float wantYaw = 0.0F, wantPitch = 0.0F;
        Vec3 world = this.target != null ? this.target.apply(context) : null;
        if (world == null && this.target == null && context.living() != null) {
            wantYaw = -context.headYaw() * Mth.DEG_TO_RAD;
            wantPitch = -context.headPitch() * Mth.DEG_TO_RAD;
        } else {
            if (world == null && this.target == null) world = nearestPlayer(context, this.range);
            if (world != null) {
                Vector3f at = new Matrix4f(modelToWorld).invert().transformPosition(new Vector3f((float) world.x, (float) world.y, (float) world.z));
                at.sub(pose.armature.joint(index).pivot());
                float flat = Mth.sqrt(at.x * at.x + at.z * at.z);
                // The model faces -Z: no turn looks that way.
                wantYaw = (float) Mth.atan2(-at.x, -at.z);
                wantPitch = (float) Mth.atan2(at.y, flat);
            }
        }
        wantYaw = Mth.clamp(wantYaw, -this.maxYaw * Mth.DEG_TO_RAD, this.maxYaw * Mth.DEG_TO_RAD);
        wantPitch = Mth.clamp(wantPitch, -this.maxPitch * Mth.DEG_TO_RAD, this.maxPitch * Mth.DEG_TO_RAD);
        if (!this.started || this.smoothing <= 0.0F) {
            this.yaw = wantYaw;
            this.pitch = wantPitch;
            this.started = true;
        } else {
            float follow = 1.0F - (float) Math.exp(-deltaTime * 3.0F / this.smoothing);
            this.yaw += (wantYaw - this.yaw) * follow;
            this.pitch += (wantPitch - this.pitch) * follow;
        }
        pose.rotation[index * 3] += this.pitch * this.weight;
        pose.rotation[index * 3 + 1] += this.yaw * this.weight;
    }

    private static @Nullable Vec3 nearestPlayer(ArmatureContext context, double range) {
        Level level = context.level();
        if (level == null) return null;
        Vec3 from = context.position();
        Player nearest = null;
        double best = range * range;
        for (Player player : level.players()) {
            if (player.isSpectator() || player == context.entity()) continue;
            double distance = player.distanceToSqr(from);
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        if (nearest == null) return null;
        return nearest.getEyePosition(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
    }
}
