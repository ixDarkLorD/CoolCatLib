package net.ixdarklord.coolcatcore.api.event.v2.client;

import net.ixdarklord.coolcatcore.api.event.v2.core.EventInvoker;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Item tooltips.
 */
public final class ItemTooltipEvents {
    public static final EventInvoker<Modify> MODIFY = EventInvoker.create(Modify.class, listeners -> (stack, context, flag, lines) -> {
        for (Modify listener : listeners) listener.modify(stack, context, flag, lines);
    });

    private ItemTooltipEvents() {}

    /** The finished tooltip lines, name included, to add to or change. */
    @FunctionalInterface
    public interface Modify {
        void modify(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines);
    }
}
