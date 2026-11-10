package net.ixdarklord.coolcatcore.api.event.v2.common;

import net.ixdarklord.coolcatcore.api.event.v2.core.EventInvoker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * Trading with villagers, wandering traders and other merchants, on the server.
 */
public final class MerchantEvents {
    public static final EventInvoker<Offers> OFFERS = EventInvoker.create(Offers.class, listeners -> (player, merchant, offers) -> {
        for (Offers listener : listeners) listener.onOffers(player, merchant, offers);
    });

    private MerchantEvents() {}

    /**
     * A player is about to be shown a merchant's trades: as the trading screen opens, and again when the list changes
     * while it's open (a villager levelling up). The list is this player's own: take trades out of it to hide them from
     * them (they can't make them either), or reorder it. The trades in it are the merchant's own, so don't change those.
     * <pre>{@code
     * MerchantEvents.OFFERS.register((player, merchant, offers) -> offers.removeIf(offer -> isLocked(player, offer.getResult())));
     * }</pre>
     */
    @FunctionalInterface
    public interface Offers {
        void onOffers(ServerPlayer player, Merchant merchant, MerchantOffers offers);
    }
}
