package net.ixdarklord.coolcatcanvas.internal.mixin.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.ixdarklord.coolcatcanvas.internal.client.sky.SkyboxManager;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Fabric API draws a dimension's DimensionRenderingRegistry sky renderer in place of vanilla's sky, once the sky's fog is
// set up, and cancels the rest of the pass: the skybox layers then go over that sky (SkyboxManager#afterSky). Checked
// the same way Fabric API does, at the start of the pass, so it doesn't matter which of the two injections runs first.
@Mixin(LevelRenderer.class)
public abstract class FabricLevelRendererMixin {
    @Shadow
    private @Nullable ClientLevel level;

    @Inject(method = "renderSky(Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V", at = @At("HEAD"))
    private void coolcatcanvas$noteCustomSky(PoseStack poseStack, Matrix4f projectionMatrix, float partialTick, Camera camera, boolean isFoggy, Runnable skyFogSetup, CallbackInfo ci) {
        if (this.level != null && DimensionRenderingRegistry.getSkyRenderer(this.level.dimension()) != null) SkyboxManager.customSkyRendered();
    }
}
