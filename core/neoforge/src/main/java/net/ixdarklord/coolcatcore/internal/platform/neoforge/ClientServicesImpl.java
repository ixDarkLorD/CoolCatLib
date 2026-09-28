package net.ixdarklord.coolcatcore.internal.platform.neoforge;

import net.ixdarklord.coolcatcore.api.client.registry.MenuScreenRegistry;
import net.ixdarklord.coolcatcore.internal.platform.ClientServices;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

// Key mappings, menu screens and tooltip components wait for their NeoForge registration events
// (fired on CoolCatLib: Core's mod bus, see NeoForgeClientEventHooks).
public final class ClientServicesImpl implements ClientServices {
    private static final ClientServicesImpl INSTANCE = new ClientServicesImpl();

    static final List<KeyMapping> KEY_MAPPINGS = new ArrayList<>();
    static final List<Consumer<RegisterMenuScreensEvent>> MENU_SCREENS = new ArrayList<>();
    static final List<Consumer<RegisterClientTooltipComponentFactoriesEvent>> TOOLTIP_COMPONENTS = new ArrayList<>();

    public static ClientServices get() {
        return INSTANCE;
    }

    @Override
    public void registerKeyMapping(KeyMapping mapping) {
        KEY_MAPPINGS.add(mapping);
    }

    @Override
    public <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void registerMenuScreen(Supplier<? extends MenuType<? extends M>> type, MenuScreenRegistry.ScreenFactory<M, U> factory) {
        MENU_SCREENS.add(event -> event.register(type.get(), factory::create));
    }

    @Override
    public <T extends TooltipComponent> void registerTooltipComponent(Class<T> type, Function<? super T, ? extends ClientTooltipComponent> factory) {
        TOOLTIP_COMPONENTS.add(event -> event.register(type, factory));
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        KEY_MAPPINGS.forEach(event::register);
    }

    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        MENU_SCREENS.forEach(registration -> registration.accept(event));
    }

    public static void onRegisterTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
        TOOLTIP_COMPONENTS.forEach(registration -> registration.accept(event));
    }
}
