package net.ixdarklord.coolcatcore.mixin;

import net.ixdarklord.coolcatcore.api.brewing.fabric.FabricBrewingRecipes;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Lets players put the custom brewing recipes' inputs in the brewing stand's bottle slots (also used by shift-clicking).
@Mixin(BrewingStandMenu.PotionSlot.class)
public abstract class BrewingStandPotionSlotMixin {
    @Inject(method = "mayPlaceItem", at = @At("HEAD"), cancellable = true)
    private static void coolcatcore$mayPlaceItem(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (FabricBrewingRecipes.registry().isValidInput(stack)) cir.setReturnValue(true);
    }
}
