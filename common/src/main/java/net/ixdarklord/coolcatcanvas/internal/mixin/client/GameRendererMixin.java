package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.ixdarklord.coolcatcanvas.api.client.effect.ScreenEffectScope;
import net.ixdarklord.coolcatcanvas.internal.client.cutscene.CutsceneManager;
import net.ixdarklord.coolcatcanvas.internal.client.effect.ScreenEffectManager;
import net.ixdarklord.coolcatcanvas.internal.client.gui.GuiAreaRecorder;
import net.ixdarklord.coolcatcanvas.internal.client.sky.SkyboxManager;
import net.ixdarklord.coolcatcanvas.internal.client.vfx.VfxManager;
import net.ixdarklord.coolcatcanvas.internal.client.viewport.SceneViewportManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Screen effects: updated at the start of the frame, drawn after the world (once vanilla's own post effect has run)
// and after the GUI. Skyboxes: updated at the start of the frame too (they're drawn from SkyRendererMixin).
// World effects: drawn right after the level, while its projection is set and before the hand clears its depth.
// Cutscenes: while one has the camera, the frame is drawn as with the HUD hidden (no hand, no fire or water over the
// view) and the block the player looks at isn't outlined.
// GuiAreas: what the HUD adds is recorded, and once the frame's GUI is gathered it becomes what the areas are.
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void coolcatcanvas$beginFrame(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        ScreenEffectManager.beginFrame(deltaTracker);
        SkyboxManager.beginFrame(deltaTracker);
        // Scene viewports are drawn to their own textures now, before the level, while nothing else waits to be drawn.
        SceneViewportManager.beginFrame(deltaTracker);
    }

    // Same condition vanilla renders the level under.
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
    private void coolcatcanvas$renderWorldScreenEffects(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (advanceGameTime && minecraft.isGameLoadFinished() && minecraft.level != null) {
            ScreenEffectManager.render(ScreenEffectScope.WORLD);
        }
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZLnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;)V", shift = At.Shift.AFTER))
    private void coolcatcanvas$renderWorldVfx(DeltaTracker deltaTracker, CallbackInfo ci) {
        VfxManager.render(((GameRenderer) (Object) this).getGameRenderState().levelRenderState.cameraRenderState, deltaTracker);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;endFrame()V", shift = At.Shift.AFTER))
    private void coolcatcanvas$renderScreenScreenEffects(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        ScreenEffectManager.render(ScreenEffectScope.SCREEN);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void coolcatcanvas$endFrame(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        ScreenEffectManager.endFrame();
        SkyboxManager.endFrame();
        VfxManager.endFrame();
    }

    @Inject(method = "extractGui", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
    private void coolcatlib$recordHud(CallbackInfo ci) {
        GuiAreaRecorder.begin(GuiAreaRecorder.Phase.HUD);
    }

    @Inject(method = "extractGui", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", shift = At.Shift.AFTER))
    private void coolcatlib$stopRecordingHud(CallbackInfo ci) {
        GuiAreaRecorder.end(GuiAreaRecorder.Phase.NONE);
    }

    @Inject(method = "extractGui", at = @At("TAIL"))
    private void coolcatlib$gatheredGui(CallbackInfo ci) {
        GuiAreaRecorder.endFrame();
    }

    @Inject(method = "extractOptions", at = @At("TAIL"))
    private void coolcatcanvas$hideHandInCutscenes(CallbackInfo ci) {
        if (CutsceneManager.hasCamera()) ((GameRenderer) (Object) this).getGameRenderState().optionsRenderState.hideGui = true;
    }

    @Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void coolcatcanvas$noBlockOutlineInCutscenes(CallbackInfoReturnable<Boolean> cir) {
        if (CutsceneManager.hasCamera()) cir.setReturnValue(false);
    }

    @Inject(method = "resize", at = @At("TAIL"))
    private void coolcatcanvas$resizeScreenEffects(int width, int height, CallbackInfo ci) {
        ScreenEffectManager.onResize();
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void coolcatcanvas$close(CallbackInfo ci) {
        ScreenEffectManager.close();
        SkyboxManager.close();
        VfxManager.close();
        SceneViewportManager.close();
    }
}
