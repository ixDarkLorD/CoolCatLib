package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.ixdarklord.coolcatcanvas.api.client.effect.ScreenEffectScope;
import net.ixdarklord.coolcatcanvas.internal.client.effect.ScreenEffectManager;
import net.ixdarklord.coolcatcanvas.internal.client.gui.GuiAreaRecorder;
import net.ixdarklord.coolcatcanvas.internal.client.sky.SkyboxManager;
import net.ixdarklord.coolcatcanvas.internal.client.viewport.SceneViewportManager;
import net.ixdarklord.coolcatcanvas.internal.client.vfx.GhostRenderer;
import net.ixdarklord.coolcatcanvas.internal.client.vfx.VfxManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Screen effects: updated at the start of the frame, drawn after the world (once vanilla's own post effect has run)
// and after the GUI. Skyboxes: updated at the start of the frame too (they're drawn from LevelRendererMixin). Both
// rebuild once vanilla's shaders are (re)loaded, which isn't reached if vanilla's own shaders failed. World effects:
// drawn right after the level, with the projection it was drawn with, before the hand clears its depth. GuiAreas: the
// HUD is recorded while Gui.render runs (ForgeGui's overlays and Fabric's HUD callbacks all run inside it), and the
// frame's areas are kept once it is all drawn.
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render(FJZ)V", at = @At("HEAD"))
    private void coolcatcanvas$beginFrame(float partialTicks, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        ScreenEffectManager.beginFrame(partialTicks);
        SkyboxManager.beginFrame(partialTicks);
        // Scene viewports are drawn to their own targets now, before the level, while nothing else waits to be drawn.
        SceneViewportManager.beginFrame(partialTicks);
    }

    // Where the main target is bound again for the GUI, only reached after the level was rendered.
    @Inject(method = "render(FJZ)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;bindWrite(Z)V", ordinal = 0))
    private void coolcatcanvas$renderWorldScreenEffects(float partialTicks, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        ScreenEffectManager.render(ScreenEffectScope.WORLD);
    }

    @Inject(method = "renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V"))
    private void coolcatcanvas$beginWorldVfx(float partialTicks, long finishTimeNano, PoseStack poseStack, CallbackInfo ci) {
        VfxManager.beginLevel();
    }

    @Inject(method = "renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V", shift = At.Shift.AFTER))
    private void coolcatcanvas$renderWorldVfx(float partialTicks, long finishTimeNano, PoseStack poseStack, CallbackInfo ci) {
        VfxManager.render(partialTicks);
    }

    @Inject(method = "render(FJZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;F)V"))
    private void coolcatlib$recordHud(float partialTicks, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        GuiAreaRecorder.begin(GuiAreaRecorder.Phase.HUD);
    }

    @Inject(method = "render(FJZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;F)V", shift = At.Shift.AFTER))
    private void coolcatlib$stopRecordingHud(float partialTicks, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        GuiAreaRecorder.end(GuiAreaRecorder.Phase.NONE);
    }

    @Inject(method = "render(FJZ)V", at = @At("RETURN"))
    private void coolcatlib$drewGui(float partialTicks, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        GuiAreaRecorder.endFrame();
    }

    @Inject(method = "render(FJZ)V", at = @At("TAIL"))
    private void coolcatcanvas$renderScreenScreenEffects(float partialTicks, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        if (!Minecraft.getInstance().noRender) ScreenEffectManager.render(ScreenEffectScope.SCREEN);
    }

    @Inject(method = "reloadShaders(Lnet/minecraft/server/packs/resources/ResourceProvider;)V", at = @At("TAIL"))
    private void coolcatcanvas$reloadEffectsAndSkyboxes(ResourceProvider resourceProvider, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        ScreenEffectManager.reload(minecraft.getResourceManager());
        SkyboxManager.reload(minecraft.getResourceManager());
        SceneViewportManager.onResourceReload();
        GhostRenderer.close();
        VfxManager.reload();
    }

    @Inject(method = "close()V", at = @At("TAIL"))
    private void coolcatcanvas$close(CallbackInfo ci) {
        ScreenEffectManager.close();
        SkyboxManager.close();
        SceneViewportManager.close();
        GhostRenderer.close();
        VfxManager.close();
    }
}
