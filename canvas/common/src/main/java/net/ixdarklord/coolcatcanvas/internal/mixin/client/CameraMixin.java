package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.ixdarklord.coolcatcanvas.internal.client.camera.CameraShakeManager;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Camera shake: once the camera has taken its entity's place and facing, it is turned a little further. It is done
// here and not later so that the view and the culling frustum are built from the final facing.
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private float xRot;
    @Shadow
    private float yRot;

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    public abstract Vec3 getPosition();

    @Inject(method = "setup", at = @At("TAIL"))
    private void coolcatcanvas$shakeCamera(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        if (!CameraShakeManager.active()) return;
        CameraShakeManager.update(this.getPosition(), partialTick);
        float yaw = CameraShakeManager.yaw();
        float pitch = CameraShakeManager.pitch();
        if (yaw != 0.0F || pitch != 0.0F) this.setRotation(this.yRot + yaw, this.xRot + pitch);
    }
}
