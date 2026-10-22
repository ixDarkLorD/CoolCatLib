package net.ixdarklord.coolcatcore.internal.mixin.client;

import net.ixdarklord.coolcatcore.api.event.v2.client.ItemTooltipEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

// ItemTooltipEvents.APPEND: at the return of getTooltipLines, after Forge's tooltip event and Fabric's tooltip callback
// (both fired before it returns). The high priority puts it after other mods' injections at the same place. A list
// that isn't a plain mutable one is left alone.
@Mixin(value = ItemStack.class, priority = 2000)
public abstract class ItemStackMixin {
    @Inject(method = "getTooltipLines", at = @At("RETURN"))
    private void coolcatcore$appendTooltip(@Nullable Player player, TooltipFlag flag, CallbackInfoReturnable<List<Component>> cir) {
        if (ItemTooltipEvents.APPEND.hasListeners() && cir.getReturnValue() instanceof ArrayList<Component> lines) {
            ItemTooltipEvents.APPEND.invoker().append((ItemStack) (Object) this, player, flag, lines);
        }
    }
}
