package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.ixdarklord.coolcatcanvas.internal.client.gui.GuiAreaRecorder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// GuiAreas: what a screen draws in its own render pass is recorded, not the tooltip drawn after it, nor its background:
// 1.21.1's render draws the background itself, so recording pauses while renderBackground runs (the container
// screens' own renderBackground, with their panel art and the inventory's player, is AbstractContainerScreenMixin's).
@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Unique
    private GuiAreaRecorder.Phase coolcatlib$wasRecording = GuiAreaRecorder.Phase.NONE;

    @Inject(method = "renderWithTooltip", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
    private void coolcatlib$recordScreen(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        this.coolcatlib$wasRecording = GuiAreaRecorder.begin(GuiAreaRecorder.Phase.SCREEN);
    }

    @Inject(method = "renderWithTooltip", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", shift = At.Shift.AFTER))
    private void coolcatlib$stopRecordingScreen(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        GuiAreaRecorder.end(this.coolcatlib$wasRecording);
    }

    @Inject(method = "renderBackground", at = @At("HEAD"))
    private void coolcatlib$pauseForBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        GuiAreaRecorder.pause();
    }

    @Inject(method = "renderBackground", at = @At("RETURN"))
    private void coolcatlib$resumeAfterBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        GuiAreaRecorder.resume();
    }
}
