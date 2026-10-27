package net.ixdarklord.coolcatcore.internal.event;

import net.minecraft.world.MenuProvider;
import org.jetbrains.annotations.Nullable;

/**
 * PlayerEvents.OPEN_MENU, shared by the common ServerPlayerMixin and a loader's own way of opening menus (NeoForge's
 * ServerPlayerMenuMixin). Implemented by ServerPlayer.
 */
public interface MenuOpening {
    /** Whether a menu is being opened vanilla's way, which asked already. */
    boolean coolcatlib$isOpeningMenu();

    /** Asks about a menu: whether it's refused. */
    boolean coolcatlib$refusesMenu(@Nullable MenuProvider provider);
}
