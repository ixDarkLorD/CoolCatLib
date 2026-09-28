package net.ixdarklord.coolcatcore.internal.core.forge;

import net.ixdarklord.coolcatcore.api.config.client.ConfigScreens;
import net.ixdarklord.coolcatcore.internal.config.ConfigManager;
import net.ixdarklord.coolcatcore.internal.event.forge.ForgeClientEventHooks;
import net.ixdarklord.coolcatcore.internal.platform.forge.ClientServicesImpl;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

// The client half of ForgeSetup; only loaded on a client.
final class ForgeClientSetup {
    private ForgeClientSetup() {}

    static void init(IEventBus modBus) {
        ForgeClientEventHooks.register(modBus);
        modBus.addListener(EventPriority.NORMAL, false, FMLClientSetupEvent.class, event -> event.enqueueWork(() -> {
            ClientServicesImpl.registerMenuScreens();
            registerConfigScreens();
        }));
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
