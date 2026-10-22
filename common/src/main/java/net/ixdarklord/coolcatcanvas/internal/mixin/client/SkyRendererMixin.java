package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ixdarklord.coolcatcanvas.api.client.sky.SkyLayerStage;
import net.ixdarklord.coolcatcanvas.api.client.sky.VanillaSky;
import net.ixdarklord.coolcatcanvas.internal.client.sky.SkyboxManager;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.world.level.MoonPhase;
import org.joml.Vector4f;
import org.joml.Vector3fc;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Skyboxes: layers drawn inside vanilla's sky pass, around the sun, moon and stars, and vanilla's parts faded out as
// they're hidden. Hooks SkyRenderer rather than LevelRenderer's sky pass, which NeoForge rewrites; both loaders call
// these same methods from it.
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void coolcatcanvas$extractSkyboxes(ClientLevel level, float partialTicks, Camera camera, SkyRenderState state, CallbackInfo ci) {
        SkyboxManager.extract(state);
    }

    // ---- Overworld-style skies ----

    @Inject(method = "renderSkyDisc", at = @At("HEAD"), cancellable = true)
    private void coolcatcanvas$hideSkyDisc(RenderPass pass, Vector3fc skyColor, CallbackInfo ci) {
        if (SkyboxManager.hidden(VanillaSky.SKY) >= 1.0F) ci.cancel();
    }

    @ModifyVariable(method = "renderSkyDisc", at = @At("HEAD"), argsOnly = true)
    private Vector3fc coolcatcanvas$fadeSkyDisc(Vector3fc skyColor) {
        return SkyboxManager.skyColor(skyColor);
    }

    @ModifyVariable(method = "renderSunriseAndSunset", at = @At("HEAD"), argsOnly = true)
    private Vector4fc coolcatcanvas$fadeSunrise(Vector4fc sunriseAndSunsetColor) {
        float hidden = SkyboxManager.hidden(VanillaSky.SUNRISE);
        return hidden > 0.0F ? new Vector4f(sunriseAndSunsetColor.x(), sunriseAndSunsetColor.y(), sunriseAndSunsetColor.z(), sunriseAndSunsetColor.w() * (1.0F - hidden)) : sunriseAndSunsetColor;
    }

    @Inject(method = "renderSunMoonAndStars", at = @At("HEAD"))
    private void coolcatcanvas$renderLayersBehindCelestials(RenderPass pass, PoseStack poseStack, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase, float rainBrightness, float starBrightness, CallbackInfo ci) {
        SkyboxManager.render(SkyLayerStage.BEHIND_CELESTIALS, pass);
    }

    @Inject(method = "renderSunMoonAndStars", at = @At("TAIL"))
    private void coolcatcanvas$renderLayersAboveCelestials(RenderPass pass, PoseStack poseStack, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase, float rainBrightness, float starBrightness, CallbackInfo ci) {
        SkyboxManager.render(SkyLayerStage.ABOVE_CELESTIALS, pass);
    }

    // Vanilla skips the stars entirely at 0 brightness.
    @ModifyVariable(method = "renderSunMoonAndStars", at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private float coolcatcanvas$fadeStars(float starBrightness) {
        return starBrightness * (1.0F - SkyboxManager.hidden(VanillaSky.STARS));
    }

    @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
    private void coolcatcanvas$hideSun(RenderPass pass, float rainBrightness, PoseStack poseStack, CallbackInfo ci) {
        if (SkyboxManager.hidden(VanillaSky.SUN) >= 1.0F) ci.cancel();
    }

    @ModifyVariable(method = "renderSun", at = @At("HEAD"), argsOnly = true)
    private float coolcatcanvas$fadeSun(float rainBrightness) {
        return rainBrightness * (1.0F - SkyboxManager.hidden(VanillaSky.SUN));
    }

    @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
    private void coolcatcanvas$hideMoon(RenderPass pass, MoonPhase moonPhase, float rainBrightness, PoseStack poseStack, CallbackInfo ci) {
        if (SkyboxManager.hidden(VanillaSky.MOON) >= 1.0F) ci.cancel();
    }

    @ModifyVariable(method = "renderMoon", at = @At("HEAD"), argsOnly = true)
    private float coolcatcanvas$fadeMoon(float rainBrightness) {
        return rainBrightness * (1.0F - SkyboxManager.hidden(VanillaSky.MOON));
    }

    // The dark disc is opaque black: it can't fade, so it goes once the skybox is half there.
    @Inject(method = "renderDarkDisc", at = @At("HEAD"), cancellable = true)
    private void coolcatcanvas$hideDarkDisc(RenderPass pass, CallbackInfo ci) {
        if (SkyboxManager.hidden(VanillaSky.VOID) >= 0.5F) ci.cancel();
    }

    // ---- The End ----

    @Inject(method = "renderEndSky", at = @At("HEAD"), cancellable = true)
    private void coolcatcanvas$hideEndSky(RenderPass pass, CallbackInfo ci) {
        if (SkyboxManager.hidden(VanillaSky.END_SKY) >= 1.0F) {
            coolcatcanvas$renderEndLayers(pass);
            ci.cancel();
        }
    }

    @Inject(method = "renderEndSky", at = @At("TAIL"))
    private void coolcatcanvas$renderLayersAfterEndSky(RenderPass pass, CallbackInfo ci) {
        coolcatcanvas$renderEndLayers(pass);
    }

    // The end sky's transforms written again with a colour (vanilla writes none): white, faded as a skybox hides it. An
    // argument swap rather than a redirect, which the MixinExtras NeoForge ships can't read.
    @ModifyArg(method = "renderEndSky", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/commands/RenderPass;setUniform(Ljava/lang/String;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;)V"), index = 1)
    private com.mojang.renderpearl.api.buffers.GpuBufferSlice coolcatcanvas$fadeEndSky(com.mojang.renderpearl.api.buffers.GpuBufferSlice transforms) {
        float hidden = SkyboxManager.hidden(VanillaSky.END_SKY);
        if (hidden <= 0.0F) return transforms;
        return com.mojang.blaze3d.systems.RenderSystem.getDynamicUniforms().writeTransform(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrixCopy(), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F - hidden));
    }

    @Inject(method = "renderEndFlash", at = @At("HEAD"), cancellable = true)
    private void coolcatcanvas$hideEndFlash(RenderPass pass, PoseStack poseStack, float intensity, float xAngle, float yAngle, CallbackInfo ci) {
        if (SkyboxManager.hidden(VanillaSky.END_FLASH) >= 1.0F) ci.cancel();
    }

    @ModifyVariable(method = "renderEndFlash", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float coolcatcanvas$fadeEndFlash(float intensity) {
        return intensity * (1.0F - SkyboxManager.hidden(VanillaSky.END_FLASH));
    }

    private static void coolcatcanvas$renderEndLayers(RenderPass pass) {
        SkyboxManager.render(SkyLayerStage.BEHIND_CELESTIALS, pass);
        SkyboxManager.render(SkyLayerStage.ABOVE_CELESTIALS, pass);
    }
}
