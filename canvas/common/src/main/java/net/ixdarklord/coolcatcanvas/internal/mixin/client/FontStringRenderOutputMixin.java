package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.ixdarklord.coolcatcanvas.api.utils.ColorGradient;
import net.ixdarklord.coolcatcanvas.internal.text.GradientTextColors;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Gradient text colors: every glyph drawn gets the next index, and a gradient color picks its color from it and the
// time. Covers all text, GUI and world (signs, name tags) alike. (1.21.1 turns the style's color into the glyph's in
// Font.StringRenderOutput.accept, reading TextColor.getValue.)
@Mixin(targets = "net.minecraft.client.gui.Font$StringRenderOutput")
public abstract class FontStringRenderOutputMixin {
    @Unique
    private int coolcatcanvas$nextIndex;
    @Unique
    private int coolcatcanvas$index;

    @Inject(method = "accept", at = @At("HEAD"))
    private void coolcatcanvas$countGlyph(int position, Style style, int codePoint, CallbackInfoReturnable<Boolean> cir) {
        this.coolcatcanvas$index = this.coolcatcanvas$nextIndex++;
    }

    @Redirect(method = "accept", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/TextColor;getValue()I"))
    private int coolcatcanvas$gradientColor(TextColor textColor) {
        ColorGradient gradient = GradientTextColors.get(textColor);
        return gradient != null ? gradient.color(this.coolcatcanvas$index, 0xFF) & 0xFFFFFF : textColor.getValue();
    }
}
