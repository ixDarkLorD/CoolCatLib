package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ixdarklord.coolcatcanvas.api.client.sky.SkyLayerStage;
import net.ixdarklord.coolcatcanvas.api.client.sky.VanillaSky;
import net.ixdarklord.coolcatcanvas.internal.client.sky.SkyboxManager;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Skyboxes: layers drawn inside vanilla's sky pass, around the sun, moon and stars, and vanilla's parts faded out as
// they're hidden. The sky pass is one method, the same on every loader (NeoForge and Forge only add an early return
// for dimensions with their own sky renderer), so these hook its calls by order.
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    private static final String SET_SHADER_COLOR = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderColor(FFFF)V";
    private static final String SET_SHADER_TEXTURE = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/resources/ResourceLocation;)V";
    private static final String BLEND_FUNC_SEPARATE = "Lcom/mojang/blaze3d/systems/RenderSystem;blendFuncSeparate(Lcom/mojang/blaze3d/platform/GlStateManager$SourceFactor;Lcom/mojang/blaze3d/platform/GlStateManager$DestFactor;Lcom/mojang/blaze3d/platform/GlStateManager$SourceFactor;Lcom/mojang/blaze3d/platform/GlStateManager$DestFactor;)V";

    // Keeps the camera and angles layers follow; draws a whole sky in dimensions without one while a skybox needs it.
    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    private void coolcatcanvas$beginSky(Matrix4f frustumMatrix, Matrix4f projectionMatrix, float partialTick, Camera camera, boolean isFoggy, Runnable skyFogSetup, CallbackInfo ci) {
        if (SkyboxManager.beginSky(frustumMatrix, projectionMatrix, partialTick, camera, skyFogSetup)) ci.cancel();
    }

    // ---- Overworld-style skies ----

    @ModifyVariable(method = "renderSky", at = @At("STORE"), ordinal = 0)
    private Vec3 coolcatcanvas$fadeSkyColor(Vec3 skyColor) {
        return SkyboxManager.skyColor(skyColor);
    }

    @ModifyVariable(method = "renderSky", at = @At("STORE"), ordinal = 0)
    private float[] coolcatcanvas$fadeSunrise(float[] sunriseColor) {
        return SkyboxManager.sunriseColor(sunriseColor);
    }

    // After the sky color and the sunrise glow, as the sun's blending is set up.
    @Inject(method = "renderSky", at = @At(value = "INVOKE", target = BLEND_FUNC_SEPARATE, ordinal = 0))
    private void coolcatcanvas$renderLayersBehindCelestials(Matrix4f frustumMatrix, Matrix4f projectionMatrix, float partialTick, Camera camera, boolean isFoggy, Runnable skyFogSetup, CallbackInfo ci) {
        SkyboxManager.render(SkyLayerStage.BEHIND_CELESTIALS);
    }

    // The sun and moon's color: white, faded by rain.
    @Inject(method = "renderSky", at = @At(value = "INVOKE", target = SET_SHADER_COLOR, ordinal = 2, shift = At.Shift.AFTER))
    private void coolcatcanvas$fadeSun(Matrix4f frustumMatrix, Matrix4f projectionMatrix, float partialTick, Camera camera, boolean isFoggy, Runnable skyFogSetup, CallbackInfo ci) {
        SkyboxManager.fadeCelestial(VanillaSky.SUN);
    }

    // The moon's texture, right before its quad.
    @Inject(method = "renderSky", at = @At(value = "INVOKE", target = SET_SHADER_TEXTURE, ordinal = 1))
    private void coolcatcanvas$fadeMoon(Matrix4f frustumMatrix, Matrix4f projectionMatrix, float partialTick, Camera camera, boolean isFoggy, Runnable skyFogSetup, CallbackInfo ci) {
        SkyboxManager.fadeCelestial(VanillaSky.MOON);
    }

    @Inject(method = "renderSky", at = @At(value = "INVOKE", target = SET_SHADER_COLOR, ordinal = 3, shift = At.Shift.AFTER))
    private void coolcatcanvas$fadeStars(Matrix4f frustumMatrix, Matrix4f projectionMatrix, float partialTick, Camera camera, boolean isFoggy, Runnable skyFogSetup, CallbackInfo ci) {
        SkyboxManager.fadeStars();
    }

    // After the stars, as the sky's color is reset.
    @Inject(method = "renderSky", at = @At(value = "INVOKE", target = SET_SHADER_COLOR, ordinal = 4))
    private void coolcatcanvas$renderLayersAboveCelestials(Matrix4f frustumMatrix, Matrix4f projectionMatrix, float partialTick, Camera camera, boolean isFoggy, Runnable skyFogSetup, CallbackInfo ci) {
        SkyboxManager.render(SkyLayerStage.ABOVE_CELESTIALS);
    }

    // The camera's height above the horizon: the dark disc is drawn below it. It's opaque black and can't fade, so it
    // goes once the skybox is half there.
    @ModifyVariable(method = "renderSky", at = @At("STORE"), ordinal = 0)
    private double coolcatcanvas$hideDarkDisc(double heightAboveHorizon) {
        return SkyboxManager.hidden(VanillaSky.VOID) >= 0.5F ? Math.max(heightAboveHorizon, 0.0) : heightAboveHorizon;
    }

    // ---- The End ----

    @Inject(method = "renderEndSky", at = @At("HEAD"), cancellable = true)
    private void coolcatcanvas$beginEndSky(PoseStack poseStack, CallbackInfo ci) {
        if (SkyboxManager.beginEndSky()) ci.cancel();
    }

    @Inject(method = "renderEndSky", at = @At("TAIL"))
    private void coolcatcanvas$endEndSky(PoseStack poseStack, CallbackInfo ci) {
        SkyboxManager.endEndSky();
    }
}
