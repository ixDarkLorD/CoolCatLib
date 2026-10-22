package net.ixdarklord.coolcatcore.api.event.v2.client;

import net.ixdarklord.coolcatcore.api.event.v2.core.EventInvoker;
import net.ixdarklord.coolcatcore.api.event.v2.core.EventPhase;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Item tooltips.
 * <ul>
 *     <li>{@link #MODIFY}: the tooltip as the loader's tooltip event builds it, to add to or change anywhere. Its phases
 *     reach other mods' own tooltip listeners: {@link EventPhase#FIRST} runs before them, {@link EventPhase#LAST} after
 *     them (NeoForge's highest and lowest priorities, Fabric phases before and after the default one).</li>
 *     <li>{@link #APPEND}: lines added at the very end, once every loader event and every other mod has had its say,
 *     so they close the tooltip: a mod's own section that should never land among others' lines.</li>
 * </ul>
 */
public final class ItemTooltipEvents {
    public static final EventInvoker<Modify> MODIFY = EventInvoker.create(Modify.class, listeners -> (stack, context, flag, lines) -> {
        for (Modify listener : listeners) listener.modify(stack, context, flag, lines);
    });

    /**
     * Fired when {@link ItemStack#getTooltipLines} returns, after the loaders' tooltip events: its lines end the
     * tooltip. Not fired for an item that hides its tooltip.
     */
    public static final EventInvoker<Append> APPEND = EventInvoker.create(Append.class, listeners -> (stack, context, player, flag, lines) -> {
        for (Append listener : listeners) listener.append(stack, context, player, flag, lines);
    });

    private ItemTooltipEvents() {}

    /** The finished tooltip lines, name included, to add to or change. */
    @FunctionalInterface
    public interface Modify {
        void modify(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines);
    }

    /** The tooltip as it is about to be shown, every other line in place: what is added here comes last. */
    @FunctionalInterface
    public interface Append {
        /**
         * @param player the player the tooltip is for, when there is one
         * @param lines  the tooltip's lines, to add to (a mutable list)
         */
        void append(ItemStack stack, Item.TooltipContext context, @Nullable Player player, TooltipFlag flag, List<Component> lines);
    }
}
