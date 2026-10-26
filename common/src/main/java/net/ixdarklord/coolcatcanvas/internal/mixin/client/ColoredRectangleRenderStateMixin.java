package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.ixdarklord.coolcatcanvas.internal.client.gui.GuiOpacity;
import net.minecraft.client.renderer.state.gui.ColoredRectangleRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// FadedItems.withOpacity: filled rectangles drawn inside it (an item's durability bar) fade with it.
@Mixin(ColoredRectangleRenderState.class)
public abstract class ColoredRectangleRenderStateMixin {
    @Shadow @Final @Mutable
    private int col1;
    @Shadow @Final @Mutable
    private int col2;

    @Inject(method = "<init>(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/client/gui/render/TextureSetup;Lorg/joml/Matrix3x2fc;IIIIIILnet/minecraft/client/gui/navigation/ScreenRectangle;Lnet/minecraft/client/gui/navigation/ScreenRectangle;)V",
            at = @At("TAIL"))
    private void coolcatlib$fade(CallbackInfo ci) {
        this.col1 = GuiOpacity.fade(this.col1, false);
        this.col2 = GuiOpacity.fade(this.col2, false);
    }
}
