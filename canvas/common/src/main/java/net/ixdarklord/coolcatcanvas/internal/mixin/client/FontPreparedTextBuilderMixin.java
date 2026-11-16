package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.ixdarklord.coolcatcanvas.api.utils.ColorGradient;
import net.ixdarklord.coolcatcanvas.internal.text.GradientTextColors;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.ARGB;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Gradient text colors: every glyph laid out gets the next index, and a gradient color picks its color from it and the
// time. Covers all text, GUI and world (signs, name tags) alike.
@Mixin(targets = "net.minecraft.client.gui.Font$PreparedTextBuilder")
public abstract class FontPreparedTextBuilderMixin {
    @Shadow
    @Final
    private int color;

    @Unique
    private int coolcatcanvas$nextIndex;
    @Unique
    private int coolcatcanvas$index;

    @Inject(method = "accept(ILnet/minecraft/network/chat/Style;Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;)Z", at = @At("HEAD"))
    private void coolcatcanvas$countGlyph(int position, Style style, BakedGlyph glyph, CallbackInfoReturnable<Boolean> cir) {
        this.coolcatcanvas$index = this.coolcatcanvas$nextIndex++;
    }

    @Inject(method = "getTextColor", at = @At("HEAD"), cancellable = true)
    private void coolcatcanvas$gradientColor(@Nullable TextColor textColor, CallbackInfoReturnable<Integer> cir) {
        ColorGradient gradient = GradientTextColors.get(textColor);
        if (gradient != null) {
            cir.setReturnValue(gradient.color(this.coolcatcanvas$index, ARGB.alpha(this.color)));
        }
    }
}
