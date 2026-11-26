package net.ixdarklord.coolcatcanvas.internal.mixin.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.ixdarklord.coolcatcanvas.internal.client.vfx.VfxManager;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The view the level is drawn with, read once the level has set it: a shader mod (Iris) moves the view bobbing out of
// the projection into this matrix, so the world effects can't rebuild the view from the camera alone.
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4fStack;mul(Lorg/joml/Matrix4fc;)Lorg/joml/Matrix4f;", ordinal = 0, shift = At.Shift.AFTER))
    private void coolcatcanvas$worldVfxView(CallbackInfo ci) {
        VfxManager.levelView(RenderSystem.getModelViewStack());
    }
}
