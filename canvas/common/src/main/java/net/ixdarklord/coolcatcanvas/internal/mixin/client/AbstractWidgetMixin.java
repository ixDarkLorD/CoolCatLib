package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.ixdarklord.coolcatcanvas.internal.client.gui.ElementOutlineRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// ElementOutlines: outlines are drawn right after the widget.
@Mixin(AbstractWidget.class)
public abstract class AbstractWidgetMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void coolcatcanvas$drawOutline(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ElementOutlineRenderer.render((AbstractWidget) (Object) this, graphics);
    }
}
