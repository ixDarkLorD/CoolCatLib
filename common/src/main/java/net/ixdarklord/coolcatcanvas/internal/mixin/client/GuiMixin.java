package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.ixdarklord.coolcatcanvas.internal.client.cutscene.CutsceneOverlay;
import net.ixdarklord.coolcatcanvas.internal.client.gui.HudTakeoverManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Cutscenes: letterbox bars, fades and subtitles are drawn before the HUD, and a cutscene that hides the HUD stops
// it there, other mods' additions to it included.
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void coolcatcanvas$cutsceneOverlay(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (CutsceneOverlay.extract(graphics, deltaTracker)) {
            ci.cancel();
            return;
        }
        // A mod looking through something of its own (HudTakeover) draws that in the HUD's place.
        if (HudTakeoverManager.extract(graphics, deltaTracker)) ci.cancel();
    }
}
