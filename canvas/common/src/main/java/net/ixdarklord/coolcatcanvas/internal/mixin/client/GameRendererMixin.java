package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.ixdarklord.coolcatcanvas.api.client.effect.ScreenEffectStage;
import net.ixdarklord.coolcatcanvas.internal.client.effect.ScreenEffectManager;
import net.ixdarklord.coolcatcanvas.internal.client.sky.SkyboxManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Screen effects: updated at the start of the frame, drawn after the world (once vanilla's own post effect has run)
// and after the GUI. Skyboxes: updated at the start of the frame too (they're drawn from SkyRendererMixin).
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void coolcatcanvas$beginFrame(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        ScreenEffectManager.beginFrame(deltaTracker);
        SkyboxManager.beginFrame(deltaTracker);
    }

    // Same condition vanilla renders the level under.
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
    private void coolcatcanvas$renderWorldScreenEffects(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (advanceGameTime && minecraft.isGameLoadFinished() && minecraft.level != null) {
            ScreenEffectManager.render(ScreenEffectStage.WORLD);
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;endFrame()V", shift = At.Shift.AFTER))
    private void coolcatcanvas$renderScreenScreenEffects(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        ScreenEffectManager.render(ScreenEffectStage.SCREEN);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void coolcatcanvas$endFrame(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        ScreenEffectManager.endFrame();
        SkyboxManager.endFrame();
    }

    @Inject(method = "resize", at = @At("TAIL"))
    private void coolcatcanvas$resizeScreenEffects(int width, int height, CallbackInfo ci) {
        ScreenEffectManager.onResize();
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void coolcatcanvas$close(CallbackInfo ci) {
        ScreenEffectManager.close();
        SkyboxManager.close();
    }
}
