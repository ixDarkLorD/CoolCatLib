package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.ixdarklord.coolcatcanvas.api.cutscene.CameraPose;
import net.ixdarklord.coolcatcanvas.internal.client.camera.CameraShakeManager;
import net.ixdarklord.coolcatcanvas.internal.client.camera.CameraZoomManager;
import net.ixdarklord.coolcatcanvas.internal.client.cutscene.CutsceneManager;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Cutscenes: once the camera has taken its entity's place and facing, a playing cutscene moves it to its own, with
// its tilt and field of view. Camera shake: then it is turned a little further. Both are done here and not later so
// that the projection and the culling frustum are built from the final view.
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private float xRot;
    @Shadow
    private float yRot;
    @Shadow
    private Vec3 position;
    @Shadow
    private boolean detached;
    @Shadow
    @Final
    private Quaternionf rotation;
    @Shadow
    @Final
    private Vector3f up;
    @Shadow
    @Final
    private Vector3f left;
    @Shadow
    private int matrixPropertiesDirty;

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    public abstract Vec3 position();

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void coolcatcanvas$moveCamera(float partialTicks, CallbackInfo ci) {
        float roll = 0.0F;
        CameraPose pose = CutsceneManager.camera(partialTicks, this.position, this.yRot, this.xRot);
        if (pose != null) {
            // More than a step from the player's eyes, the player is part of the scene: their body is drawn.
            if (pose.position().distanceToSqr(this.position) > 0.36) this.detached = true;
            this.setPosition(pose.position());
            this.setRotation(pose.yaw(), pose.pitch());
            roll = pose.roll();
        }

        if (CameraShakeManager.active()) {
            CameraShakeManager.update(this.position(), partialTicks);
            float yaw = CameraShakeManager.yaw();
            float pitch = CameraShakeManager.pitch();
            if (yaw != 0.0F || pitch != 0.0F) this.setRotation(this.yRot + yaw, this.xRot + pitch);
        }

        // Setting the rotation levels the camera, so the tilt goes on last.
        if (roll != 0.0F) {
            this.rotation.rotateZ(-roll * Mth.DEG_TO_RAD);
            this.up.set(0.0F, 1.0F, 0.0F).rotate(this.rotation);
            this.left.set(-1.0F, 0.0F, 0.0F).rotate(this.rotation);
            this.matrixPropertiesDirty |= 3;
        }
    }

    // A cutscene's field of view first; a zoom (CameraZoom) narrows whatever that leaves.
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void coolcatcanvas$cutsceneFov(float partialTicks, CallbackInfoReturnable<Float> cir) {
        boolean cutscene = CutsceneManager.hasCamera();
        boolean zoomed = CameraZoomManager.active();
        if (!cutscene && !zoomed) return;
        float fov = cir.getReturnValue();
        if (cutscene) fov = CutsceneManager.fov(fov);
        if (zoomed) fov = CameraZoomManager.fov(fov, partialTicks);
        cir.setReturnValue(fov);
    }
}
