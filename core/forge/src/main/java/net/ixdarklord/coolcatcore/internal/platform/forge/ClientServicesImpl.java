package net.ixdarklord.coolcatcore.internal.platform.forge;

import net.ixdarklord.coolcatcore.api.client.registry.MenuScreenRegistry;
import net.ixdarklord.coolcatcore.internal.platform.ClientServices;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.ixdarklord.coolcatcore.api.network.CustomPacketPayload;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

// Key mappings and tooltip components wait for their Forge registration events, fired on CoolCatLib: Core's mod bus
// (see register). Forge 47 has no menu screen event: screens go into MenuScreens during client setup, once menu types
// are registered, or straight away after it.
public final class ClientServicesImpl implements ClientServices {
    private static final ClientServicesImpl INSTANCE = new ClientServicesImpl();

    private static final List<KeyMapping> KEY_MAPPINGS = new ArrayList<>();
    private static final List<Runnable> MENU_SCREENS = new ArrayList<>();
    private static final List<Consumer<RegisterClientTooltipComponentFactoriesEvent>> TOOLTIP_COMPONENTS = new ArrayList<>();
    private static boolean menuScreensRegistered;

    public static ClientServices get() {
        return INSTANCE;
    }

    /** Hooks the client registrations into Forge; {@code modBus} is CoolCatLib: Core's. */
    public static void register(IEventBus modBus) {
        modBus.addListener(EventPriority.NORMAL, false, RegisterKeyMappingsEvent.class, ClientServicesImpl::onRegisterKeyMappings);
        modBus.addListener(EventPriority.NORMAL, false, RegisterClientTooltipComponentFactoriesEvent.class, ClientServicesImpl::onRegisterTooltipComponents);
        modBus.addListener(EventPriority.NORMAL, false, FMLClientSetupEvent.class, event -> event.enqueueWork(ClientServicesImpl::registerMenuScreens));
    }

    @Override
    public void registerKeyMapping(KeyMapping mapping) {
        synchronized (KEY_MAPPINGS) {
            KEY_MAPPINGS.add(mapping);
        }
    }

    @Override
    public <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void registerMenuScreen(Supplier<? extends MenuType<? extends M>> type, MenuScreenRegistry.ScreenFactory<M, U> factory) {
        Runnable registration = () -> MenuScreens.<M, U>register(type.get(), factory::create);
        synchronized (MENU_SCREENS) {
            if (!menuScreensRegistered) {
                MENU_SCREENS.add(registration);
                return;
            }
        }
        registration.run();
    }

    @Override
    public <T extends TooltipComponent> void registerTooltipComponent(Class<T> type, Function<? super T, ? extends ClientTooltipComponent> factory) {
        synchronized (TOOLTIP_COMPONENTS) {
            TOOLTIP_COMPONENTS.add(event -> event.register(type, factory));
        }
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ForgeNetworking.sendToServer(payload);
    }

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        synchronized (KEY_MAPPINGS) {
            KEY_MAPPINGS.forEach(event::register);
        }
    }

    /** Called on the main thread during client setup. */
    public static void registerMenuScreens() {
        List<Runnable> pending;
        synchronized (MENU_SCREENS) {
            menuScreensRegistered = true;
            pending = List.copyOf(MENU_SCREENS);
            MENU_SCREENS.clear();
        }
        pending.forEach(Runnable::run);
    }

    public static void onRegisterTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
        synchronized (TOOLTIP_COMPONENTS) {
            TOOLTIP_COMPONENTS.forEach(registration -> registration.accept(event));
        }
    }
}
