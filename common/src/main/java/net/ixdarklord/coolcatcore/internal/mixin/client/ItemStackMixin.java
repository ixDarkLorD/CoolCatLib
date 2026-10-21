package net.ixdarklord.coolcatcore.internal.mixin.client;

import net.ixdarklord.coolcatcore.api.event.v2.client.ItemTooltipEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

// ItemTooltipEvents.APPEND: at the return of getTooltipLines, after NeoForge's tooltip event and Fabric's tooltip
// callback (both fired before it returns). The high priority puts it after other mods' injections at the same place.
// A tooltip the item hides (an immutable empty or warning list) is left alone.
@Mixin(value = ItemStack.class, priority = 2000)
public abstract class ItemStackMixin {
    @Inject(method = "getTooltipLines", at = @At("RETURN"))
    private void coolcatcore$appendTooltip(Item.TooltipContext context, @Nullable Player player, TooltipFlag flag, CallbackInfoReturnable<List<Component>> cir) {
        if (ItemTooltipEvents.APPEND.hasListeners() && cir.getReturnValue() instanceof ArrayList<Component> lines) {
            ItemTooltipEvents.APPEND.invoker().append((ItemStack) (Object) this, context, player, flag, lines);
        }
    }
}
