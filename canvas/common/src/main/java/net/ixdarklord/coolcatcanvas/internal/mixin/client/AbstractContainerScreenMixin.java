package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.ixdarklord.coolcatcanvas.internal.client.gui.GuiAreaRecorder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// GuiAreas: a container screen's renderBackground (its own, not Screen's) draws the dimmed backdrop and renderBg, its
// panel art and the inventory's player; none of it is recorded, while its slots, items, labels and widgets are.
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Inject(method = "renderBackground", at = @At("HEAD"))
    private void coolcatlib$pauseForBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        GuiAreaRecorder.pause();
    }

    @Inject(method = "renderBackground", at = @At("RETURN"))
    private void coolcatlib$resumeAfterBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        GuiAreaRecorder.resume();
    }
}
