package net.ixdarklord.coolcatcore.mixin.neoforge;

import net.ixdarklord.coolcatcore.internal.event.MenuOpening;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;
import java.util.function.Consumer;

// PlayerEvents.OPEN_MENU for the menus mods open with extra data, NeoForge's own way; vanilla's way comes here too, and
// was asked already (the common ServerPlayerMixin's). NeoForge's own method, so not remapped.
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMenuMixin {
    @Inject(method = "openMenu(Lnet/minecraft/world/MenuProvider;Ljava/util/function/Consumer;)Ljava/util/OptionalInt;", at = @At("HEAD"), cancellable = true, remap = false)
    private void coolcatlib$beforeOpenMenuWithData(@Nullable MenuProvider provider, Consumer<RegistryFriendlyByteBuf> extraData, CallbackInfoReturnable<OptionalInt> cir) {
        MenuOpening self = (MenuOpening) this;
        if (!self.coolcatlib$isOpeningMenu() && self.coolcatlib$refusesMenu(provider)) cir.setReturnValue(OptionalInt.empty());
    }
}
