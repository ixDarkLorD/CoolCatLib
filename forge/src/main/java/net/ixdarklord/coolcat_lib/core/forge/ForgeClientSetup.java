package net.ixdarklord.coolcat_lib.core.forge;

import net.ixdarklord.coolcat_lib.core.Constants;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ForgeClientSetup {
    @SubscribeEvent
    public static void init(final FMLClientSetupEvent event) {}
}
