package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.event.RecipeMatching;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// RecipeEvents.MATCH: a campfire is who looks its recipes up as it cooks, and with whoever puts food on it.
@Mixin(CampfireBlockEntity.class)
public abstract class CampfireBlockEntityMixin {
    @Inject(method = "cookTick", at = @At("HEAD"))
    private static void coolcatlib$enterCook(ServerLevel level, BlockPos pos, BlockState state, CampfireBlockEntity entity,
                                             RecipeManager.CachedCheck<SingleRecipeInput, CampfireCookingRecipe> recipeCache, CallbackInfo ci) {
        RecipeMatching.enter(entity, null);
    }

    @Inject(method = "cookTick", at = @At("RETURN"))
    private static void coolcatlib$exitCook(ServerLevel level, BlockPos pos, BlockState state, CampfireBlockEntity entity,
                                            RecipeManager.CachedCheck<SingleRecipeInput, CampfireCookingRecipe> recipeCache, CallbackInfo ci) {
        RecipeMatching.exit();
    }

    @Inject(method = "placeFood", at = @At("HEAD"))
    private void coolcatlib$enterPlace(ServerLevel level, @Nullable LivingEntity sourceEntity, ItemStack placeItem, CallbackInfoReturnable<Boolean> cir) {
        RecipeMatching.enter((CampfireBlockEntity) (Object) this, sourceEntity instanceof Player player ? player : null);
    }

    @Inject(method = "placeFood", at = @At("RETURN"))
    private void coolcatlib$exitPlace(ServerLevel level, @Nullable LivingEntity sourceEntity, ItemStack placeItem, CallbackInfoReturnable<Boolean> cir) {
        RecipeMatching.exit();
    }
}
