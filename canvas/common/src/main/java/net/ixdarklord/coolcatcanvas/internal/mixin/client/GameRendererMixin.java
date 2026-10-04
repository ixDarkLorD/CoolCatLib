package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.ixdarklord.coolcatcanvas.api.client.effect.ScreenEffectScope;
import net.ixdarklord.coolcatcanvas.internal.client.effect.ScreenEffectManager;
import net.ixdarklord.coolcatcanvas.internal.client.sky.SkyboxManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Screen effects: updated at the start of the frame, drawn after the world (once vanilla's own post effect has run)
// and after the GUI. Skyboxes: updated at the start of the frame too (they're drawn from LevelRendererMixin). Both
// rebuild once the new shaders are in place (not reached if vanilla's own shaders failed).
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void coolcatcanvas$beginFrame(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        ScreenEffectManager.beginFrame(deltaTracker);
        SkyboxManager.beginFrame(deltaTracker);
    }

    // Inside the block that renders the level, after vanilla's post effect, as the main target is bound for the GUI.
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;bindWrite(Z)V", ordinal = 0))
    private void coolcatcanvas$renderWorldScreenEffects(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        ScreenEffectManager.render(ScreenEffectScope.WORLD);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;flush()V", shift = At.Shift.AFTER))
    private void coolcatcanvas$renderScreenScreenEffects(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        ScreenEffectManager.render(ScreenEffectScope.SCREEN);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void coolcatcanvas$endFrame(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
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

    // The provider only holds shaders/; definitions and textures come from the (already swapped) resource manager.
    @Inject(method = "reloadShaders", at = @At("TAIL"))
    private void coolcatcanvas$reloadEffectsAndSkyboxes(ResourceProvider resourceProvider, CallbackInfo ci) {
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        ScreenEffectManager.reload(manager);
        SkyboxManager.reload(manager);
    }
}
