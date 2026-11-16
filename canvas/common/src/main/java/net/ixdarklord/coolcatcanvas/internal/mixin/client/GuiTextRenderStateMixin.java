package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.ixdarklord.coolcatcanvas.internal.client.gui.GuiOpacity;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// ItemOpacityRenderer.withOpacity: text drawn inside it (an item's count) fades with it.
@Mixin(GuiTextRenderState.class)
public abstract class GuiTextRenderStateMixin {
    @Shadow @Final @Mutable
    private int color;
    @Shadow @Final @Mutable
    private int backgroundColor;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void coolcatlib$fade(CallbackInfo ci) {
        this.color = GuiOpacity.fade(this.color, true);
        this.backgroundColor = GuiOpacity.fade(this.backgroundColor, false);
    }
}
