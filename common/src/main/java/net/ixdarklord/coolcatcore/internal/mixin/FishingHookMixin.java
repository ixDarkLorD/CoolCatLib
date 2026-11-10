package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.api.event.v2.common.PlayerEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Collection;
import java.util.List;

// PlayerEvents.ITEM_FISHED: the fishing loot table's catch, as it's handed to the advancement trigger, before it's thrown
// to the player (the same list is thrown, so what listeners add comes too).
@Mixin(FishingHook.class)
public abstract class FishingHookMixin {
    @ModifyArg(method = "retrieve", index = 3, at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/advancements/criterion/FishingRodHookedTrigger;trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/projectile/FishingHook;Ljava/util/Collection;)V"))
    private Collection<ItemStack> coolcatlib$fished(Collection<ItemStack> items) {
        FishingHook hook = (FishingHook) (Object) this;
        if (!PlayerEvents.ITEM_FISHED.hasListeners() || !(hook.getPlayerOwner() instanceof ServerPlayer player) || !(items instanceof List<ItemStack> list)) return items;
        PlayerEvents.ITEM_FISHED.invoker().onItemFished(player, hook, list);
        return items;
    }
}
