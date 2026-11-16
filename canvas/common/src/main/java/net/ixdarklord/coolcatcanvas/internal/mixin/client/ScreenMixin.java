package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.ixdarklord.coolcatcanvas.internal.client.gui.GuiAreaRecorder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// GuiAreas: what a screen draws in its own render pass is recorded, not the tooltip drawn after it. On 1.20.1 a screen
// draws its background from its own render, so renderBackground (and the dirt, and Forge's BackgroundRendered event
// fired in it) pauses recording.
@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Unique
    private GuiAreaRecorder.Phase coolcatlib$wasRecording = GuiAreaRecorder.Phase.NONE;

    @Inject(method = "renderWithTooltip(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
    private void coolcatlib$recordScreen(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        this.coolcatlib$wasRecording = GuiAreaRecorder.begin(GuiAreaRecorder.Phase.SCREEN);
    }

    @Inject(method = "renderWithTooltip(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", shift = At.Shift.AFTER))
    private void coolcatlib$stopRecordingScreen(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        GuiAreaRecorder.end(this.coolcatlib$wasRecording);
    }

    @Inject(method = "renderBackground(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("HEAD"))
    private void coolcatlib$pauseForBackground(GuiGraphics graphics, CallbackInfo ci) {
        GuiAreaRecorder.pause();
    }

    @Inject(method = "renderBackground(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("RETURN"))
    private void coolcatlib$resumeAfterBackground(GuiGraphics graphics, CallbackInfo ci) {
        GuiAreaRecorder.resume();
    }
}
