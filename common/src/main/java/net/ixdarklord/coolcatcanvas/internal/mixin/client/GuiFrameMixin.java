package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.ixdarklord.coolcatcanvas.internal.client.gui.GuiAreaRecorder;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// GuiAreas: what the HUD adds is recorded, and once the frame's GUI is gathered it becomes what the areas are.
@Mixin(Gui.class)
public abstract class GuiFrameMixin {
    @Inject(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Hud;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
    private void coolcatlib$recordHud(CallbackInfo ci) {
        GuiAreaRecorder.begin(GuiAreaRecorder.Phase.HUD);
    }

    @Inject(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Hud;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", shift = At.Shift.AFTER))
    private void coolcatlib$stopRecordingHud(CallbackInfo ci) {
        GuiAreaRecorder.end(GuiAreaRecorder.Phase.NONE);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void coolcatlib$gatheredGui(CallbackInfo ci) {
        GuiAreaRecorder.endFrame();
    }
}
