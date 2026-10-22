package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.api.brewing.BrewingRecipeRegistry;
import net.ixdarklord.coolcatcore.internal.brewing.CustomBrewing;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.BrewingInput;
import net.minecraft.world.item.crafting.BrewingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

// Brewing recipes made in code (RegisterBrewingRecipesEvent) brew alongside the data pack ones.
@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandBlockEntityMixin {
    @Shadow
    @Final
    private RecipeManager.CachedCheck<BrewingInput, BrewingRecipe> quickCheck;

    @Inject(method = "isBrewable", at = @At("RETURN"), cancellable = true)
    private static void coolcatlib$customBrewable(ServerLevel level, BrewingStandBlockEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;
        BrewingRecipeRegistry registry = CustomBrewing.registry();
        for (int slot = 0; slot < 3; slot++) {
            if (registry.hasOutput(entity.getItem(slot), entity.getItem(3))) {
                cir.setReturnValue(true);
                return;
            }
        }
    }

    // When a recipe made in code brews any of the three, the brew is done here: those from it, the rest as vanilla does.
    @Inject(method = "doBrew", at = @At("HEAD"), cancellable = true)
    private static void coolcatlib$customBrew(ServerLevel level, BlockPos pos, BrewingStandBlockEntity entity, CallbackInfo ci) {
        BrewingRecipeRegistry registry = CustomBrewing.registry();
        ItemStack ingredient = entity.getItem(3);
        ItemStack[] custom = new ItemStack[3];
        boolean any = false;
        for (int slot = 0; slot < 3; slot++) {
            custom[slot] = registry.getOutput(entity.getItem(slot), ingredient);
            any |= !custom[slot].isEmpty();
        }
        if (!any) return;
        for (int slot = 0; slot < 3; slot++) {
            if (!custom[slot].isEmpty()) {
                entity.setItem(slot, custom[slot]);
                continue;
            }
            ItemStack container = entity.getItem(slot);
            BrewingInput input = new BrewingInput(container, ingredient);
            Optional<RecipeHolder<BrewingRecipe>> recipe = ((BrewingStandBlockEntityMixin) (Object) entity).quickCheck.getRecipeFor(input, level);
            entity.setItem(slot, recipe.isPresent() ? recipe.get().value().assemble(input) : container);
        }
        ItemStackTemplate remainder = ingredient.getItem().getCraftingRemainder();
        ingredient.shrink(1);
        if (remainder != null) {
            if (ingredient.isEmpty()) ingredient = remainder.create();
            else Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), remainder.create());
        }
        entity.setItem(3, ingredient);
        level.levelEvent(1035, pos, 0);
        ci.cancel();
    }

    @Inject(method = "canPlaceItem", at = @At("RETURN"), cancellable = true)
    private void coolcatlib$customPlace(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;
        if (slot == 3 && CustomBrewing.registry().isValidIngredient(stack)) cir.setReturnValue(true);
        else if (slot < 3 && ((BrewingStandBlockEntity) (Object) this).getItem(slot).isEmpty() && CustomBrewing.registry().isValidInput(stack)) cir.setReturnValue(true);
    }
}
