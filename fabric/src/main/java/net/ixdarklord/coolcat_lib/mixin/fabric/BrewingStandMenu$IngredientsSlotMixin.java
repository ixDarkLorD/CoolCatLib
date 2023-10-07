package net.ixdarklord.coolcat_lib.mixin.fabric;

import net.ixdarklord.coolcat_lib.common.brewing.fabric.BrewingRecipeRegistry;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.inventory.BrewingStandMenu$IngredientsSlot")
public abstract class BrewingStandMenu$IngredientsSlotMixin {
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void mayPlace$New(ItemStack itemStack, CallbackInfoReturnable<Boolean> cir) {
        if (BrewingRecipeRegistry.isValidIngredient(itemStack))
            cir.setReturnValue(true);
    }
}
