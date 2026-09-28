package net.ixdarklord.coolcatcore.mixin;

import net.ixdarklord.coolcatcore.api.brewing.fabric.FabricBrewingRecipes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Lets hoppers and the like put the custom brewing recipes' inputs in the bottle slots (vanilla only takes potions and bottles).
@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandBlockEntityMixin {
    @Shadow
    public abstract ItemStack getItem(int index);

    @Inject(method = "canPlaceItem", at = @At("HEAD"), cancellable = true)
    private void coolcatcore$canPlaceItem(int index, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (index < 3 && this.getItem(index).isEmpty() && FabricBrewingRecipes.registry().isValidInput(stack)) {
            cir.setReturnValue(true);
        }
    }
}
