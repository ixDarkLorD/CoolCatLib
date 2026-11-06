package net.ixdarklord.coolcatcore.internal.mixin.forge;

import net.ixdarklord.coolcatcore.api.event.v2.common.PlayerEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraftforge.network.NetworkHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

// PlayerEvents.OPEN_MENU for the menus mods open with extra data, which Forge opens itself rather than through
// ServerPlayer.openMenu (the common ServerPlayerMixin's). Its other openScreen methods come here.
@Mixin(value = NetworkHooks.class, remap = false)
public abstract class NetworkHooksMixin {
    @Inject(method = "openScreen(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/MenuProvider;Ljava/util/function/Consumer;)V", at = @At("HEAD"), cancellable = true)
    private static void coolcatlib$beforeOpenScreen(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> extraData, CallbackInfo ci) {
        if (PlayerEvents.OPEN_MENU.hasListeners() && PlayerEvents.OPEN_MENU.invoker().onOpenMenu(player, provider).isInterrupt()) ci.cancel();
    }
}
