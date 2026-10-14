package net.ixdarklord.coolcatcore.api.menu;

import net.ixdarklord.coolcatcore.internal.platform.CommonServices;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

import java.util.function.Consumer;

/**
 * Menus that send extra data to the client when they open (which item, which block...).
 */
public final class ExtendedMenus {
    private ExtendedMenus() {}

    /**
     * A menu type whose client-side menu is built from the data written by {@link #open}. Register it in
     * {@code Registries.MENU}.
     */
    public static <T extends AbstractContainerMenu> MenuType<T> create(Factory<T> factory) {
        return CommonServices.get().createExtendedMenuType(factory);
    }

    /**
     * Opens a menu for the player, writing the data its client-side menu is built from.
     */
    public static void open(ServerPlayer player, MenuProvider provider, Consumer<RegistryFriendlyByteBuf> data) {
        CommonServices.get().openExtendedMenu(player, provider, data);
    }

    /**
     * Opens a menu of an extended type without extra data.
     */
    public static void open(ServerPlayer player, MenuProvider provider) {
        open(player, provider, buf -> {});
    }

    @FunctionalInterface
    public interface Factory<T extends AbstractContainerMenu> {
        /** Builds the client's menu; {@code data} is what the server wrote when opening it. */
        T create(int containerId, Inventory inventory, RegistryFriendlyByteBuf data);
    }
}
