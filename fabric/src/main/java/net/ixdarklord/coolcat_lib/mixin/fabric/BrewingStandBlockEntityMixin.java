package net.ixdarklord.coolcat_lib.mixin.fabric;

import net.ixdarklord.coolcat_lib.common.brewing.fabric.BrewingHandler;
import net.ixdarklord.coolcat_lib.common.brewing.fabric.BrewingRecipeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandBlockEntityMixin {
    @Shadow @Final private static int[] SLOTS_FOR_SIDES;
    @Shadow public abstract ItemStack getItem(int i);

    @Inject(method = "isBrewable", at = @At("HEAD"), cancellable = true)
    private static void isBrewable$New(NonNullList<ItemStack> nonNullList, CallbackInfoReturnable<Boolean> cir) {
        ItemStack itemStack = nonNullList.get(3);
        if (!itemStack.isEmpty()) {
            if (BrewingRecipeRegistry.canBrew(nonNullList, itemStack, SLOTS_FOR_SIDES)) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "doBrew", at = @At("HEAD"), cancellable = true)
    private static void doBrew$New(Level level, BlockPos blockPos, NonNullList<ItemStack> nonNullList, CallbackInfo ci) {
        if (BrewingRecipeRegistry.canBrew(nonNullList, nonNullList.get(3), SLOTS_FOR_SIDES)) {
            BrewingHandler.doBrew(level, blockPos, nonNullList, SLOTS_FOR_SIDES);
            ci.cancel();
        }
    }

    @Inject(method = "canPlaceItem", at = @At("HEAD"), cancellable = true)
    public void canPlaceItem$New(int i, ItemStack itemStack, CallbackInfoReturnable<Boolean> cir) {
        if (i == 3) {
            if (BrewingRecipeRegistry.isValidIngredient(itemStack))
                cir.setReturnValue(true);
        } else if (i != 4) {
            if (BrewingRecipeRegistry.isValidInput(itemStack) && this.getItem(i).isEmpty())
                cir.setReturnValue(true);
        }
    }
}
