package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.event.RecipeMatching;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// RecipeEvents.MATCH: a furnace, smoker or blast furnace is who looks its recipes up, as it ticks and when its input
// changes (for the cooking time).
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin {
    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void coolcatlib$enterTick(Level level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity entity, CallbackInfo ci) {
        RecipeMatching.enter(entity, null);
    }

    @Inject(method = "serverTick", at = @At("RETURN"))
    private static void coolcatlib$exitTick(Level level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity entity, CallbackInfo ci) {
        RecipeMatching.exit();
    }

    @Inject(method = "getTotalCookTime", at = @At("HEAD"))
    private static void coolcatlib$enterCookTime(Level level, AbstractFurnaceBlockEntity entity, CallbackInfoReturnable<Integer> cir) {
        RecipeMatching.enter(entity, null);
    }

    @Inject(method = "getTotalCookTime", at = @At("RETURN"))
    private static void coolcatlib$exitCookTime(Level level, AbstractFurnaceBlockEntity entity, CallbackInfoReturnable<Integer> cir) {
        RecipeMatching.exit();
    }
}
