package net.ixdarklord.coolcatcanvas.internal.mixin.client;

import net.ixdarklord.coolcatcanvas.internal.client.gui.HudTakeoverManager;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// A mod looking through something of its own (HudTakeover) draws that in the HUD's place, and the HUD stops here, other
// mods' additions to it included. (Forge draws its own HUD, which doesn't come here: its Pre event does the same.)
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;F)V", at = @At("HEAD"), cancellable = true)
    private void coolcatcanvas$hudTakeover(GuiGraphics graphics, float partialTick, CallbackInfo ci) {
        if (HudTakeoverManager.render(graphics, partialTick)) ci.cancel();
    }
}
