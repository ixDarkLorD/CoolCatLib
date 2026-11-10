package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.api.event.v2.common.EnchantEvents;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

// EnchantEvents.TABLE_OPTIONS: an offer's enchantments, both for its hint and when it's taken.
@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuMixin {
    @Unique
    private Player coolcatlib$player;

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("RETURN"))
    private void coolcatlib$rememberPlayer(int containerId, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
        this.coolcatlib$player = inventory.player;
    }

    @Inject(method = "getEnchantmentList", at = @At("RETURN"), cancellable = true)
    private void coolcatlib$options(RegistryAccess access, ItemStack item, int slot, int cost, CallbackInfoReturnable<List<EnchantmentInstance>> cir) {
        if (this.coolcatlib$player == null || this.coolcatlib$player.level().isClientSide() || !EnchantEvents.TABLE_OPTIONS.hasListeners()) return;
        List<EnchantmentInstance> enchantments = new ArrayList<>(cir.getReturnValue());
        EnchantEvents.TABLE_OPTIONS.invoker().onTableOptions(this.coolcatlib$player, item, slot, enchantments);
        cir.setReturnValue(enchantments);
    }
}
