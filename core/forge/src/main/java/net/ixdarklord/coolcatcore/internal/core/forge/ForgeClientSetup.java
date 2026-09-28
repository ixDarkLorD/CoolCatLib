package net.ixdarklord.coolcatcore.internal.core.forge;

import net.ixdarklord.coolcatcore.api.config.client.ConfigScreens;
import net.ixdarklord.coolcatcore.api.core.ClientModConstructor;
import net.ixdarklord.coolcatcore.api.item.ComponentItem;
import net.ixdarklord.coolcatcore.internal.config.ConfigManager;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.ixdarklord.coolcatcore.internal.core.client.CoolCatCoreClientConstructor;
import net.ixdarklord.coolcatcore.internal.event.forge.ForgeClientEventHooks;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

// CoolCatLib: Core's client half, run by ForgeSetup on a client only.
public final class ForgeClientSetup {
    private ForgeClientSetup() {}

    static void init(IEventBus modEventBus) {
        ForgeClientEventHooks.register(modEventBus);
        MinecraftForge.EVENT_BUS.addListener((ItemTooltipEvent event) -> ComponentItem.onTooltip(event.getItemStack(), event.getToolTip()));
        ClientModConstructor.construct(CoolCatCore.MOD_ID, CoolCatCoreClientConstructor::new);
        modEventBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(ForgeClientSetup::registerConfigScreens));
    }

    // Every mod with a CoolCatLib: Core config gets a "Config" button in the mod list, unless it set its own screen.
    private static void registerConfigScreens() {
        for (String modId : ConfigManager.modIds()) {
            ModList.get().getModContainerById(modId).ifPresent(container -> {
                if (container.getCustomExtension(ConfigScreenHandler.ConfigScreenFactory.class).isEmpty()) {
                    container.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                            () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> ConfigScreens.create(parent, modId)));
                }
            });
        }
    }
}
