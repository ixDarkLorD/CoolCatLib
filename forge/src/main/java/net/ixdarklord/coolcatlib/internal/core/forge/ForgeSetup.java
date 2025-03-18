package net.ixdarklord.coolcatlib.internal.core.forge;

import net.ixdarklord.coolcatlib.api.hooks.ServerLifecycleHooks;
import net.ixdarklord.coolcatlib.internal.core.CoolCatLib;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(CoolCatLib.MOD_ID)
public class ForgeSetup {
    public ForgeSetup() {
        MinecraftForge.EVENT_BUS.addListener((ServerStartingEvent event) -> ServerLifecycleHooks.updateServerState(event.getServer()));
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> ServerLifecycleHooks.updateServerState(null));
    }
}