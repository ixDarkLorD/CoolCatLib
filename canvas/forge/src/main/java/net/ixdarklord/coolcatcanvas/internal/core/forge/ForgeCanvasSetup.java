package net.ixdarklord.coolcatcanvas.internal.core.forge;

import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvasConstructor;
import net.ixdarklord.coolcatcanvas.internal.core.client.CoolCatCanvasClientConstructor;
import net.ixdarklord.coolcatcore.api.core.forge.ForgeModEntrypoint;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(CoolCatCanvas.MOD_ID)
public class ForgeCanvasSetup extends ForgeModEntrypoint {
    public ForgeCanvasSetup(FMLJavaModLoadingContext context) {
        super(context);
        this.common(CoolCatCanvasConstructor::new);
        this.client(() -> CoolCatCanvasClientConstructor::new);
    }
}
