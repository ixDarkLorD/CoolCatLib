package net.ixdarklord.coolcatcanvas.internal.core.forge;

import net.ixdarklord.coolcatcanvas.internal.client.gui.HudTakeoverManager;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.common.MinecraftForge;

// CoolCatLib Canvas's Forge client hooks, registered by ForgeCanvasSetup on a client only.
final class ForgeCanvasClientHooks {
    private ForgeCanvasClientHooks() {}

    static void register() {
        // HudTakeover: Forge's HUD doesn't go through Gui#render (GuiMixin), and stops here when this is cancelled.
        MinecraftForge.EVENT_BUS.addListener((RenderGuiEvent.Pre event) -> {
            if (HudTakeoverManager.render(event.getGuiGraphics(), event.getPartialTick())) event.setCanceled(true);
        });
    }
}
