package net.ixdarklord.coolcatcore.api.client.registry;

import net.ixdarklord.coolcatcore.internal.platform.ClientServices;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

import java.util.function.Supplier;

/**
 * The screens menus open. Register while the client initialises.
 */
public final class MenuScreenRegistry {
    private MenuScreenRegistry() {}

    /**
     * @param type the menu type, supplied so it can be a {@code RegistryEntry} not yet registered
     */
    public static <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void register(Supplier<? extends MenuType<? extends M>> type, ScreenFactory<M, U> factory) {
        ClientServices.get().registerMenuScreen(type, factory);
    }

    /** Vanilla's own constructor type is private; this takes its place (usually {@code MyScreen::new}). */
    @FunctionalInterface
    public interface ScreenFactory<M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> {
        U create(M menu, Inventory inventory, Component title);
    }
}
