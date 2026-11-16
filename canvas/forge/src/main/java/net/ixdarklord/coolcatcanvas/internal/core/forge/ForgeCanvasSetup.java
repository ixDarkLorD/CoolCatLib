package net.ixdarklord.coolcatcanvas.internal.core.forge;

import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvasConstructor;
import net.ixdarklord.coolcatcanvas.internal.core.client.CoolCatCanvasClientConstructor;
import net.ixdarklord.coolcatcore.api.core.forge.ForgeModEntrypoint;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

// CoolCatLib Canvas's Forge entry point: constructed by Forge as its own mod, built on CoolCatLib.
@Mod(CoolCatCanvas.MOD_ID)
public class ForgeCanvasSetup extends ForgeModEntrypoint {
    public ForgeCanvasSetup(FMLJavaModLoadingContext context) {
        super(context);
        this.common(CoolCatCanvasConstructor::new);
        this.client(() -> CoolCatCanvasClientConstructor::new);
        // Forge 47 has no client-only @Mod classes; the client hooks are only loaded on a client.
        if (FMLEnvironment.dist.isClient()) ForgeCanvasClientHooks.register();
    }
}
