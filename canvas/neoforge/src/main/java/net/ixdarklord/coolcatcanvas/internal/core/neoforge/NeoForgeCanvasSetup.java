package net.ixdarklord.coolcatcanvas.internal.core.neoforge;

import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvasConstructor;
import net.ixdarklord.coolcatcanvas.internal.core.client.CoolCatCanvasClientConstructor;
import net.ixdarklord.coolcatcore.api.core.neoforge.NeoForgeModEntrypoint;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(CoolCatCanvas.MOD_ID)
public class NeoForgeCanvasSetup extends NeoForgeModEntrypoint {
    public NeoForgeCanvasSetup(ModContainer container) {
        super(container);
        this.common(CoolCatCanvasConstructor::new);
        this.client(() -> CoolCatCanvasClientConstructor::new);
    }
}
