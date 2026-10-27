package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.event.RecipeMatching;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

// RecipeEvents.MATCH: a campfire is who looks its recipes up, as it cooks and when food is put on it.
@Mixin(CampfireBlockEntity.class)
public abstract class CampfireBlockEntityMixin {
    @Inject(method = "cookTick", at = @At("HEAD"))
    private static void coolcatlib$enterCook(Level level, BlockPos pos, BlockState state, CampfireBlockEntity entity, CallbackInfo ci) {
        RecipeMatching.enter(entity, null);
    }

    @Inject(method = "cookTick", at = @At("RETURN"))
    private static void coolcatlib$exitCook(Level level, BlockPos pos, BlockState state, CampfireBlockEntity entity, CallbackInfo ci) {
        RecipeMatching.exit();
    }

    @Inject(method = "getCookableRecipe", at = @At("HEAD"))
    private void coolcatlib$enterPlace(ItemStack stack, CallbackInfoReturnable<Optional<CampfireCookingRecipe>> cir) {
        RecipeMatching.enter((CampfireBlockEntity) (Object) this, null);
    }

    @Inject(method = "getCookableRecipe", at = @At("RETURN"))
    private void coolcatlib$exitPlace(ItemStack stack, CallbackInfoReturnable<Optional<CampfireCookingRecipe>> cir) {
        RecipeMatching.exit();
    }
}
