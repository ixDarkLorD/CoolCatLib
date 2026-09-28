package net.ixdarklord.coolcatcanvas.internal.mixin.forge;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.ixdarklord.coolcatcanvas.internal.client.sky.SkyboxManager;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Forge's sky pass starts with the dimension's own sky renderer (IForgeDimensionSpecialEffects#renderSky), and skips
// vanilla's sky when it draws one: the skybox layers then go over that sky (SkyboxManager#afterSky).
@Mixin(LevelRenderer.class)
public abstract class ForgeLevelRendererMixin {
    // A Forge method: no obfuscated name, and class names are Mojang's at runtime.
    @ModifyExpressionValue(method = "renderSky(Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/DimensionSpecialEffects;renderSky(Lnet/minecraft/client/multiplayer/ClientLevel;IFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/Camera;Lorg/joml/Matrix4f;ZLjava/lang/Runnable;)Z", remap = false))
    private boolean coolcatcanvas$noteCustomSky(boolean rendered) {
        if (rendered) SkyboxManager.customSkyRendered();
        return rendered;
    }
}
