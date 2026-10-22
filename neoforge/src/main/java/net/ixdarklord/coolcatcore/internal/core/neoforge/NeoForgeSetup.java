package net.ixdarklord.coolcatcore.internal.core.neoforge;

import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvasConstructor;
import net.ixdarklord.coolcatcore.api.core.neoforge.NeoForgeModEntrypoint;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCoreConstructor;
import net.ixdarklord.coolcatcore.internal.event.neoforge.NeoForgeEventHooks;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(CoolCatCore.MOD_ID)
public class NeoForgeSetup extends NeoForgeModEntrypoint {
    public NeoForgeSetup(ModContainer container) {
        super(container);
        NeoForgeEventHooks.register(this.modEventBus);
        this.common(CoolCatCoreConstructor::new);
        this.common(CoolCatCanvasConstructor::new);
    }
}
