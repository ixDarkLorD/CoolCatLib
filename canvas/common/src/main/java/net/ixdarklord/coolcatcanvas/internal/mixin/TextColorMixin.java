package net.ixdarklord.coolcatcanvas.internal.mixin;

import com.mojang.serialization.DataResult;
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

    @Inject(method = "parseColor", at = @At("HEAD"), cancellable = true)
    private static void coolcatcanvas$parseGradient(String color, CallbackInfoReturnable<DataResult<TextColor>> cir) {
        if (!GradientTextColors.isGradientName(color)) return;
        ColorGradient gradient = GradientTextColors.parse(color);
        cir.setReturnValue(gradient != null
                ? DataResult.success(GradientTextColors.of(gradient))
                : DataResult.error(() -> "Invalid gradient color: " + color));
    }
}
