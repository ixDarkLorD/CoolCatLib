package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.ixdarklord.coolcatcanvas.api.client.effect.ScreenEffectStage;
import net.ixdarklord.coolcatcanvas.internal.client.effect.ScreenEffectManager;
import net.ixdarklord.coolcatcanvas.internal.client.sky.SkyboxManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Screen effects: updated at the start of the frame, drawn after the world (once vanilla's own post effect has run)
// and after the GUI. Skyboxes: updated at the start of the frame too (they're drawn from LevelRendererMixin). Both
// rebuild once vanilla's shaders are (re)loaded, which isn't reached if vanilla's own shaders failed.
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render(FJZ)V", at = @At("HEAD"))
    private void coolcatcanvas$beginFrame(float partialTicks, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        ScreenEffectManager.beginFrame(partialTicks);
        SkyboxManager.beginFrame(partialTicks);
    }

    // Where the main target is bound again for the GUI, only reached after the level was rendered.
    @Inject(method = "render(FJZ)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;bindWrite(Z)V", ordinal = 0))
    private void coolcatcanvas$renderWorldScreenEffects(float partialTicks, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        ScreenEffectManager.render(ScreenEffectStage.WORLD);
    }

    @Inject(method = "render(FJZ)V", at = @At("TAIL"))
    private void coolcatcanvas$renderScreenScreenEffects(float partialTicks, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        if (!Minecraft.getInstance().noRender) ScreenEffectManager.render(ScreenEffectStage.SCREEN);
    }

    @Inject(method = "reloadShaders(Lnet/minecraft/server/packs/resources/ResourceProvider;)V", at = @At("TAIL"))
    private void coolcatcanvas$reloadEffectsAndSkyboxes(ResourceProvider resourceProvider, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        ScreenEffectManager.reload(minecraft.getResourceManager());
        SkyboxManager.reload(minecraft.getResourceManager());
    }

    @Inject(method = "close()V", at = @At("TAIL"))
    private void coolcatcanvas$close(CallbackInfo ci) {
        ScreenEffectManager.close();
        SkyboxManager.close();
    }
}
