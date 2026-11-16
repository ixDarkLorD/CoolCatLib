package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ixdarklord.coolcatcanvas.api.client.sky.SkyLayerStage;
import net.ixdarklord.coolcatcanvas.api.client.sky.VanillaSky;
import net.ixdarklord.coolcatcanvas.internal.client.sky.SkyboxManager;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.MoonPhase;
import org.joml.Vector4f;
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
    private void coolcatcanvas$hideSkyDisc(int skyColor, CallbackInfo ci) {
        if (SkyboxManager.hidden(VanillaSky.SKY) >= 1.0F) ci.cancel();
    }

    @ModifyVariable(method = "renderSkyDisc", at = @At("HEAD"), argsOnly = true)
    private int coolcatcanvas$fadeSkyDisc(int skyColor) {
        return SkyboxManager.skyColor(skyColor);
    }

    @ModifyVariable(method = "renderSunriseAndSunset", at = @At("HEAD"), argsOnly = true)
    private int coolcatcanvas$fadeSunrise(int sunriseAndSunsetColor) {
        float hidden = SkyboxManager.hidden(VanillaSky.SUNRISE);
        return hidden > 0.0F ? ARGB.multiplyAlpha(sunriseAndSunsetColor, 1.0F - hidden) : sunriseAndSunsetColor;
    }

    @Inject(method = "renderSunMoonAndStars", at = @At("HEAD"))
    private void coolcatcanvas$renderLayersBehindCelestials(PoseStack poseStack, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase, float rainBrightness, float starBrightness, CallbackInfo ci) {
        SkyboxManager.render(SkyLayerStage.BEHIND_CELESTIALS);
    }

    @Inject(method = "renderSunMoonAndStars", at = @At("TAIL"))
    private void coolcatcanvas$renderLayersAboveCelestials(PoseStack poseStack, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase, float rainBrightness, float starBrightness, CallbackInfo ci) {
        SkyboxManager.render(SkyLayerStage.ABOVE_CELESTIALS);
    }

    // Vanilla skips the stars entirely at 0 brightness.
    @ModifyVariable(method = "renderSunMoonAndStars", at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private float coolcatcanvas$fadeStars(float starBrightness) {
        return starBrightness * (1.0F - SkyboxManager.hidden(VanillaSky.STARS));
    }

    @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
    private void coolcatcanvas$hideSun(float rainBrightness, PoseStack poseStack, CallbackInfo ci) {
        if (SkyboxManager.hidden(VanillaSky.SUN) >= 1.0F) ci.cancel();
    }

    @ModifyVariable(method = "renderSun", at = @At("HEAD"), argsOnly = true)
    private float coolcatcanvas$fadeSun(float rainBrightness) {
        return rainBrightness * (1.0F - SkyboxManager.hidden(VanillaSky.SUN));
    }

    @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
    private void coolcatcanvas$hideMoon(MoonPhase moonPhase, float rainBrightness, PoseStack poseStack, CallbackInfo ci) {
        if (SkyboxManager.hidden(VanillaSky.MOON) >= 1.0F) ci.cancel();
    }

    @ModifyVariable(method = "renderMoon", at = @At("HEAD"), argsOnly = true)
    private float coolcatcanvas$fadeMoon(float rainBrightness) {
        return rainBrightness * (1.0F - SkyboxManager.hidden(VanillaSky.MOON));
    }

    // The dark disc is opaque black: it can't fade, so it goes once the skybox is half there.
    @Inject(method = "renderDarkDisc", at = @At("HEAD"), cancellable = true)
    private void coolcatcanvas$hideDarkDisc(CallbackInfo ci) {
        if (SkyboxManager.hidden(VanillaSky.VOID) >= 0.5F) ci.cancel();
    }

    // ---- The End ----

    @Inject(method = "renderEndSky", at = @At("HEAD"), cancellable = true)
    private void coolcatcanvas$hideEndSky(CallbackInfo ci) {
        if (SkyboxManager.hidden(VanillaSky.END_SKY) >= 1.0F) {
            coolcatcanvas$renderEndLayers();
            ci.cancel();
        }
    }

    @Inject(method = "renderEndSky", at = @At("TAIL"))
    private void coolcatcanvas$renderLayersAfterEndSky(CallbackInfo ci) {
        coolcatcanvas$renderEndLayers();
    }

    @ModifyArg(method = "renderEndSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/DynamicUniforms;writeTransform(Lorg/joml/Matrix4fc;Lorg/joml/Vector4fc;Lorg/joml/Vector3fc;Lorg/joml/Matrix4fc;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"), index = 1)
    private Vector4fc coolcatcanvas$fadeEndSky(Vector4fc color) {
        float hidden = SkyboxManager.hidden(VanillaSky.END_SKY);
        return hidden > 0.0F ? new Vector4f(color.x(), color.y(), color.z(), color.w() * (1.0F - hidden)) : color;
    }

    @Inject(method = "renderEndFlash", at = @At("HEAD"), cancellable = true)
    private void coolcatcanvas$hideEndFlash(PoseStack poseStack, float intensity, float xAngle, float yAngle, CallbackInfo ci) {
        if (SkyboxManager.hidden(VanillaSky.END_FLASH) >= 1.0F) ci.cancel();
    }

    @ModifyVariable(method = "renderEndFlash", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float coolcatcanvas$fadeEndFlash(float intensity) {
        return intensity * (1.0F - SkyboxManager.hidden(VanillaSky.END_FLASH));
    }

    private static void coolcatcanvas$renderEndLayers() {
        SkyboxManager.render(SkyLayerStage.BEHIND_CELESTIALS);
        SkyboxManager.render(SkyLayerStage.ABOVE_CELESTIALS);
    }
}
