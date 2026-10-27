package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.api.event.v2.common.EnchantEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// EnchantEvents.ANVIL_RESULT, once the anvil has worked out its result (whichever way it returns).
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin extends ItemCombinerMenu {
    @Shadow
    @Final
    private DataSlot cost;

    private AnvilMenuMixin(MenuType<?> type, int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(type, containerId, inventory, access);
    }

    @Inject(method = "createResult", at = @At("RETURN"))
    private void coolcatlib$result(CallbackInfo ci) {
        ItemStack result = this.resultSlots.getItem(0);
        if (result.isEmpty() || this.player.level().isClientSide() || !EnchantEvents.ANVIL_RESULT.hasListeners()) return;
        EnchantEvents.ANVIL_RESULT.invoker().onAnvilResult(this.player, this.inputSlots.getItem(0), this.inputSlots.getItem(1), result).ifInterrupt(replaced -> {
            this.resultSlots.setItem(0, replaced);
            if (replaced.isEmpty()) this.cost.set(0);
        });
    }
}
