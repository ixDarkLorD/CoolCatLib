package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.ixdarklord.coolcatcanvas.internal.client.gui.HudTakeoverManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// A mod looking through something of its own (HudTakeover) draws that in the HUD's place, and the HUD stops there,
// other mods' additions to it included.
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void coolcatcanvas$hudTakeover(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (HudTakeoverManager.extract(graphics, deltaTracker)) ci.cancel();
    }
}
