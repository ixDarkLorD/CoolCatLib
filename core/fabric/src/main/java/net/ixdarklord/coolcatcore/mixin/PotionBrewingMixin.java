package net.ixdarklord.coolcatcore.mixin;

import net.ixdarklord.coolcatcore.api.brewing.fabric.FabricBrewingRecipes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// The custom brewing recipes (FabricBrewingRecipes) next to vanilla's mixes, which are static on 1.20.1: the brewing
// stand and its menu go through these for ingredients, mixes and their results.
@Mixin(PotionBrewing.class)
public abstract class PotionBrewingMixin {
    @Inject(method = "isIngredient", at = @At("HEAD"), cancellable = true)
    private static void coolcatcore$isIngredient(ItemStack input, CallbackInfoReturnable<Boolean> cir) {
        if (FabricBrewingRecipes.registry().isValidIngredient(input)) cir.setReturnValue(true);
    }

    @Inject(method = "hasMix", at = @At("HEAD"), cancellable = true)
    private static void coolcatcore$hasMix(ItemStack input, ItemStack reagent, CallbackInfoReturnable<Boolean> cir) {
        if (FabricBrewingRecipes.registry().hasOutput(input, reagent)) cir.setReturnValue(true);
    }

    @Inject(method = "mix", at = @At("HEAD"), cancellable = true)
    private static void coolcatcore$mix(ItemStack reagent, ItemStack potion, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack output = FabricBrewingRecipes.registry().getOutput(potion, reagent);
        if (!output.isEmpty()) cir.setReturnValue(output);
    }
}
