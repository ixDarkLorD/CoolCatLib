package net.ixdarklord.coolcatcore.mixin.forge;

import net.ixdarklord.coolcatcore.api.event.v2.common.PlayerEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraftforge.common.extensions.IForgeServerPlayer;
import org.spongepowered.asm.mixin.Mixin;

import java.util.function.Consumer;

// PlayerEvents.OPEN_MENU for the menus mods open with extra data, which Forge opens itself rather than through
// ServerPlayer.openMenu (the common ServerPlayerMixin's): its default method, asked first. The one taking a BlockPos
// comes here.
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMenuMixin implements IForgeServerPlayer {
    @Override
    public void openMenu(MenuProvider provider, Consumer<FriendlyByteBuf> extraData) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (PlayerEvents.OPEN_MENU.hasListeners() && PlayerEvents.OPEN_MENU.invoker().onOpenMenu(self, provider).isInterrupt()) return;
        IForgeServerPlayer.super.openMenu(provider, extraData);
    }
}
