package net.ixdarklord.coolcatcore.internal.event;

import net.ixdarklord.coolcatcore.api.event.v2.common.MerchantEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jetbrains.annotations.Nullable;

/**
 * MerchantEvents.OFFERS: the trades a player is sent are their own list (the merchant's trades, some maybe left out),
 * kept on their trading menu, which then trades from that list too, so the client's and the server's agree on which
 * trade is which (MerchantMenuMixin, MerchantContainerMixin, ServerPlayerMixin).
 */
public final class MerchantOffersView {
    private MerchantOffersView() {}

    /** The list a player is sent instead of a merchant's own, when anyone listens. */
    public static MerchantOffers forPlayer(ServerPlayer player, int containerId, MerchantOffers offers) {
        if (!MerchantEvents.OFFERS.hasListeners() || !(player.containerMenu instanceof MerchantMenu menu) || menu.containerId != containerId) return offers;
        MerchantOffers own = new MerchantOffers();
        own.addAll(offers);
        MerchantEvents.OFFERS.invoker().onOffers(player, ((Menu) menu).coolcatlib$getTrader(), own);
        ((Menu) menu).coolcatlib$setOffers(own);
        return own;
    }

    /** The list a merchant trades from with its trading player: theirs, if they were sent one. */
    public static MerchantOffers tradedFrom(Merchant merchant, MerchantOffers offers) {
        if (merchant.getTradingPlayer() instanceof ServerPlayer player && player.containerMenu instanceof Menu menu && menu.coolcatlib$getTrader() == merchant) {
            MerchantOffers own = menu.coolcatlib$getOffers();
            if (own != null) return own;
        }
        return offers;
    }

    /** Implemented by MerchantMenu. */
    public interface Menu {
        Merchant coolcatlib$getTrader();

        @Nullable MerchantOffers coolcatlib$getOffers();

        void coolcatlib$setOffers(MerchantOffers offers);
    }
}
