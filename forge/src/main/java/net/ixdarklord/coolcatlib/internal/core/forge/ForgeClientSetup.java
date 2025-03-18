package net.ixdarklord.coolcatlib.internal.core.forge;

import net.ixdarklord.coolcatlib.internal.core.CoolCatLib;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = CoolCatLib.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ForgeClientSetup {
    @SubscribeEvent
    public static void init(final FMLClientSetupEvent event) {}
}
