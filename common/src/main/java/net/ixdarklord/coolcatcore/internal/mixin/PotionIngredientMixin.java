package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.brewing.CustomBrewing;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PotionIngredient;
import net.minecraft.world.item.crafting.RecipeAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// The inputs of brewing recipes made in code go in a brewing stand's bottle slots, by hand or with shift-click.
@Mixin(PotionIngredient.class)
public abstract class PotionIngredientMixin {
    @Inject(method = "isPotionInput", at = @At("RETURN"), cancellable = true)
    private static void coolcatlib$customInput(ItemStack stack, RecipeAccess recipes, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && CustomBrewing.registry().isValidInput(stack)) cir.setReturnValue(true);
    }
}
