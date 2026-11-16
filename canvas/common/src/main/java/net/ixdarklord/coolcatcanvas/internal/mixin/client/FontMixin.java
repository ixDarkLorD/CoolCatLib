package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.ixdarklord.coolcatcanvas.internal.client.gui.GuiAreaRecorder;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// GuiAreas: the box of each line of text, recorded where every draw of it ends (GuiGraphics.drawString on both loaders,
// whose Forge int overloads hand on to float ones, and mods drawing through the font themselves), under the matrix it
// is drawn with. Only recorded while the HUD or a screen draws, so world text (signs, name tags) never is.
@Mixin(Font.class)
public abstract class FontMixin {
    @Inject(method = "drawInternal(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;IIZ)I", at = @At("HEAD"))
    private void coolcatlib$recordString(String text, float x, float y, int color, boolean dropShadow, Matrix4f matrix,
                                         MultiBufferSource buffer, Font.DisplayMode displayMode, int backgroundColor,
                                         int packedLight, boolean bidirectional, CallbackInfoReturnable<Integer> cir) {
        if (GuiAreaRecorder.recording() && text != null) GuiAreaRecorder.record(matrix, x, y, x + this.coolcatlib$self().width(text), y + this.coolcatlib$self().lineHeight);
    }

    @Inject(method = "drawInternal(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I", at = @At("HEAD"))
    private void coolcatlib$recordSequence(FormattedCharSequence text, float x, float y, int color, boolean dropShadow,
                                           Matrix4f matrix, MultiBufferSource buffer, Font.DisplayMode displayMode,
                                           int backgroundColor, int packedLight, CallbackInfoReturnable<Integer> cir) {
        if (GuiAreaRecorder.recording()) GuiAreaRecorder.record(matrix, x, y, x + this.coolcatlib$self().width(text), y + this.coolcatlib$self().lineHeight);
    }

    @Unique
    private Font coolcatlib$self() {
        return (Font) (Object) this;
    }
}
