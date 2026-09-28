package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.ixdarklord.coolcatcanvas.api.client.sky.SkyLayerStage;
import net.ixdarklord.coolcatcanvas.api.client.sky.VanillaSky;
import net.ixdarklord.coolcatcanvas.internal.client.sky.SkyboxManager;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Skyboxes: layers drawn inside vanilla's sky pass, around the sun, moon and stars, and vanilla's parts faded out as
// they're hidden. The same code runs on both loaders. Where the dimension has a sky renderer of its own (Forge's
// DimensionSpecialEffects#renderSky, Fabric API's DimensionRenderingRegistry), that renderer skips vanilla's sky pass:
// the loader modules report it, and the layers are drawn over that sky once the pass is over (see afterSky).
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Unique
    private static final String RENDER_SKY = "renderSky(Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V";
    @Unique
    private static final String DRAW_BUFFER = "Lcom/mojang/blaze3d/vertex/VertexBuffer;drawWithShader(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/ShaderInstance;)V";
    @Unique
    private static final String DRAW_IMMEDIATE = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;)V";

    @Shadow
    private @Nullable ClientLevel level;

    @Inject(method = RENDER_SKY, at = @At("HEAD"))
    private void coolcatcanvas$beginSky(PoseStack poseStack, Matrix4f projectionMatrix, float partialTick, Camera camera, boolean isFoggy, Runnable skyFogSetup, CallbackInfo ci) {
        SkyboxManager.beginSky(poseStack.last().pose(), projectionMatrix);
    }

    // Once the sky's fog is set up: dimensions without a sky get the layers alone.
    @Inject(method = RENDER_SKY, at = @At(value = "INVOKE", target = "Ljava/lang/Runnable;run()V", ordinal = 0, shift = At.Shift.AFTER))
    private void coolcatcanvas$renderLayersWithoutSky(PoseStack poseStack, Matrix4f projectionMatrix, float partialTick, Camera camera, boolean isFoggy, Runnable skyFogSetup, CallbackInfo ci) {
        SkyboxManager.renderWithoutVanillaSky(this.level);
    }

    // Once the sky is done, whoever drew it: over a dimension's own sky renderer's sky, the layers.
    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;" + RENDER_SKY))
    private void coolcatcanvas$renderLayersOverCustomSky(LevelRenderer renderer, PoseStack poseStack, Matrix4f projectionMatrix, float partialTick, Camera camera, boolean isFoggy, Runnable skyFogSetup, Operation<Void> original) {
        original.call(renderer, poseStack, projectionMatrix, partialTick, camera, isFoggy, skyFogSetup);
        SkyboxManager.afterSky(skyFogSetup);
    }

    // ---- Overworld-style skies ----

    @WrapOperation(method = RENDER_SKY, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderColor(FFFF)V", ordinal = 0))
    private void coolcatcanvas$fadeSkyColor(float red, float green, float blue, float alpha, Operation<Void> original) {
        float[] rgb = {red, green, blue};
        SkyboxManager.skyColor(rgb);
        original.call(rgb[0], rgb[1], rgb[2], alpha);
    }

    @WrapWithCondition(method = RENDER_SKY, at = @At(value = "INVOKE", target = DRAW_BUFFER, ordinal = 0))
    private boolean coolcatcanvas$hideSkyDisc(VertexBuffer buffer, Matrix4f modelView, Matrix4f projection, ShaderInstance shader) {
        return SkyboxManager.hidden(VanillaSky.SKY) < 1.0F;
    }

    @WrapOperation(method = RENDER_SKY, at = @At(value = "INVOKE", target = DRAW_IMMEDIATE, ordinal = 0))
    private void coolcatcanvas$fadeSunrise(BufferBuilder.RenderedBuffer buffer, Operation<Void> original) {
        coolcatcanvas$drawFaded(buffer, VanillaSky.SUNRISE, original);
    }

    // Over the sky color and the sunrise glow, right before the sun: vanilla sets its blending up for it next.
    @Inject(method = RENDER_SKY, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;blendFuncSeparate(Lcom/mojang/blaze3d/platform/GlStateManager$SourceFactor;Lcom/mojang/blaze3d/platform/GlStateManager$DestFactor;Lcom/mojang/blaze3d/platform/GlStateManager$SourceFactor;Lcom/mojang/blaze3d/platform/GlStateManager$DestFactor;)V"))
    private void coolcatcanvas$renderLayersBehindCelestials(PoseStack poseStack, Matrix4f projectionMatrix, float partialTick, Camera camera, boolean isFoggy, Runnable skyFogSetup, CallbackInfo ci) {
        SkyboxManager.render(SkyLayerStage.BEHIND_CELESTIALS);
    }

    @WrapOperation(method = RENDER_SKY, at = @At(value = "INVOKE", target = DRAW_IMMEDIATE, ordinal = 1))
    private void coolcatcanvas$fadeSun(BufferBuilder.RenderedBuffer buffer, Operation<Void> original) {
        coolcatcanvas$drawFaded(buffer, VanillaSky.SUN, original);
    }

    @WrapOperation(method = RENDER_SKY, at = @At(value = "INVOKE", target = DRAW_IMMEDIATE, ordinal = 2))
    private void coolcatcanvas$fadeMoon(BufferBuilder.RenderedBuffer buffer, Operation<Void> original) {
        coolcatcanvas$drawFaded(buffer, VanillaSky.MOON, original);
    }

    // Vanilla skips the stars entirely at 0 brightness.
    @ModifyExpressionValue(method = RENDER_SKY, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getStarBrightness(F)F"))
    private float coolcatcanvas$fadeStars(float starBrightness) {
        return starBrightness * (1.0F - SkyboxManager.hidden(VanillaSky.STARS));
    }

    // Over the sun, moon and stars, where vanilla ends their blending.
    @Inject(method = RENDER_SKY, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;disableBlend()V"))
    private void coolcatcanvas$renderLayersAboveCelestials(PoseStack poseStack, Matrix4f projectionMatrix, float partialTick, Camera camera, boolean isFoggy, Runnable skyFogSetup, CallbackInfo ci) {
        SkyboxManager.render(SkyLayerStage.ABOVE_CELESTIALS);
    }

    // The dark disc is opaque black: it can't fade, so it goes once the skybox is half there.
    @WrapWithCondition(method = RENDER_SKY, at = @At(value = "INVOKE", target = DRAW_BUFFER, ordinal = 2))
    private boolean coolcatcanvas$hideDarkDisc(VertexBuffer buffer, Matrix4f modelView, Matrix4f projection, ShaderInstance shader) {
        return SkyboxManager.hidden(VanillaSky.VOID) < 0.5F;
    }

    // Draws a sun, moon or sunrise with its alpha scaled down as far as it's hidden; not at all once fully hidden.
    @Unique
    private static void coolcatcanvas$drawFaded(BufferBuilder.RenderedBuffer buffer, VanillaSky part, Operation<Void> original) {
        float hidden = SkyboxManager.hidden(part);
        if (hidden <= 0.0F) {
            original.call(buffer);
            return;
        }
        if (hidden >= 1.0F) {
            buffer.release();
            return;
        }
        float[] color = RenderSystem.getShaderColor();
        float red = color[0];
        float green = color[1];
        float blue = color[2];
        float alpha = color[3];
        RenderSystem.setShaderColor(red, green, blue, alpha * (1.0F - hidden));
        original.call(buffer);
        RenderSystem.setShaderColor(red, green, blue, alpha);
    }

    // ---- The End ----

    @Inject(method = "renderEndSky(Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("HEAD"), cancellable = true)
    private void coolcatcanvas$hideEndSky(PoseStack poseStack, CallbackInfo ci) {
        if (SkyboxManager.hidden(VanillaSky.END_SKY) >= 1.0F) {
            coolcatcanvas$renderEndLayers();
            ci.cancel();
        }
    }

    @Inject(method = "renderEndSky(Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("TAIL"))
    private void coolcatcanvas$renderLayersAfterEndSky(PoseStack poseStack, CallbackInfo ci) {
        coolcatcanvas$renderEndLayers();
    }

    @ModifyArg(method = "renderEndSky(Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;color(IIII)Lcom/mojang/blaze3d/vertex/VertexConsumer;"), index = 3)
    private int coolcatcanvas$fadeEndSky(int alpha) {
        float hidden = SkyboxManager.hidden(VanillaSky.END_SKY);
        return hidden > 0.0F ? Math.round(alpha * (1.0F - hidden)) : alpha;
    }

    @Unique
    private static void coolcatcanvas$renderEndLayers() {
        SkyboxManager.render(SkyLayerStage.BEHIND_CELESTIALS);
        SkyboxManager.render(SkyLayerStage.ABOVE_CELESTIALS);
    }
}
