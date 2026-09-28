package net.ixdarklord.coolcatcore.api.client.registry;

import net.ixdarklord.coolcatcore.internal.platform.ClientServices;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

import java.util.function.Function;

/**
 * How custom tooltip components (from {@code Item.getTooltipImage}) are drawn. Register while the client initialises.
 */
public final class TooltipComponentRegistry {
    private TooltipComponentRegistry() {}

    public static <T extends TooltipComponent> void register(Class<T> type, Function<? super T, ? extends ClientTooltipComponent> factory) {
        ClientServices.get().registerTooltipComponent(type, factory);
    }
}
