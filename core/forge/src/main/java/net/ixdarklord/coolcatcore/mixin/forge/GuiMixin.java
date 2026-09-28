package net.ixdarklord.coolcatcore.mixin.forge;

import net.ixdarklord.coolcatcore.api.event.v2.client.ClientGuiEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// ClientGuiEvents.RENDER_HUD (Forge 52.1 has no HUD render event): after vanilla's HUD layers.
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void coolcatcore$renderHud(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        if (ClientGuiEvents.RENDER_HUD.hasListeners()) ClientGuiEvents.RENDER_HUD.invoker().render(graphics, delta);
    }
}
