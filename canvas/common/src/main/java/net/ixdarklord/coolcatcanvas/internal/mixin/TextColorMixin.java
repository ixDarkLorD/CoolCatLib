package net.ixdarklord.coolcatcanvas.internal.mixin;

import net.ixdarklord.coolcatcanvas.api.utils.ColorGradient;
import net.ixdarklord.coolcatcanvas.internal.text.GradientTextColors;
import net.minecraft.network.chat.TextColor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Gradient text colors: each carries its ColorGradient, and "coolcatcanvas:rainbow..." / "coolcatcanvas:gradient/..."
// names parse back to them (JSON text, commands, the network).
@Mixin(TextColor.class)
public abstract class TextColorMixin implements GradientTextColors.Holder {
    @Unique
    private @Nullable ColorGradient coolcatcanvas$gradient;

    @Override
    public @Nullable ColorGradient coolcatcanvas$getGradient() {
        return this.coolcatcanvas$gradient;
    }

    @Override
    public void coolcatcanvas$setGradient(ColorGradient gradient) {
        this.coolcatcanvas$gradient = gradient;
    }

    // 1.20.1 compares colors by their RGB value alone: a gradient color would equal a plain color of its first stop, and
    // styles with either would merge. Gradient colors are only equal to colors of the same gradient.
    @Inject(method = "equals", at = @At("HEAD"), cancellable = true)
    private void coolcatcanvas$compareGradients(Object object, CallbackInfoReturnable<Boolean> cir) {
        if (this == object || !(object instanceof TextColor other)) return;
        ColorGradient otherGradient = GradientTextColors.get(other);
        if (this.coolcatcanvas$gradient != null || otherGradient != null) {
            cir.setReturnValue(java.util.Objects.equals(this.coolcatcanvas$gradient, otherGradient));
        }
    }

    @Inject(method = "parseColor", at = @At("HEAD"), cancellable = true)
    private static void coolcatcanvas$parseGradient(String color, CallbackInfoReturnable<TextColor> cir) {
        if (!GradientTextColors.isGradientName(color)) return;
        ColorGradient gradient = GradientTextColors.parse(color);
        // 1.20.1's parseColor gives null for a name it can't read.
        cir.setReturnValue(gradient != null ? GradientTextColors.of(gradient) : null);
    }
}
