package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.ixdarklord.coolcatcanvas.internal.client.camera.CameraZoomManager;
import net.ixdarklord.coolcatcanvas.internal.client.cutscene.CutsceneManager;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Cutscenes: while one holds the player still, moving the mouse doesn't turn them. (Their keys are released by
// CutsceneManager; the mouse has no mapping to release.) Camera zoom: slower turning, and the wheel.
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void coolcatcanvas$lockTurning(double mousea, CallbackInfo ci) {
        if (CutsceneManager.locksInput()) ci.cancel();
    }

    // Camera zoom: the mouse turns the player less by as much as the view is zoomed, as vanilla does for a spyglass.
    @ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 0)
    private double coolcatcanvas$zoomedYaw(double yaw) {
        return yaw * CameraZoomManager.sensitivity();
    }

    @ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 1)
    private double coolcatcanvas$zoomedPitch(double pitch) {
        return pitch * CameraZoomManager.sensitivity();
    }

    // Camera zoom: while a mod's wheel control is active, the wheel zooms and does nothing else.
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void coolcatcanvas$zoomScroll(long handle, double xoffset, double yoffset, CallbackInfo ci) {
        if (CameraZoomManager.onScroll(yoffset)) ci.cancel();
    }
}
