package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.event.RecipeMatching;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

// RecipeEvents.MATCH: a recipe refused is passed over for the next that matches. Both ways the game looks one up: among
// all of a type's recipes, and with the recipe it used last (which falls back to all of them). (Plain injects rather than
// redirects: the MixinExtras NeoForge ships misreads a compiled @Redirect's target.)
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {
    @Shadow
    private RecipeMap recipes;

    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;",
            at = @At("RETURN"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void coolcatlib$matching(RecipeType<T> type, I input, Level level, CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir) {
        RecipeHolder<T> found = cir.getReturnValue().orElse(null);
        if (found == null || RecipeMatching.allows(level, found)) return;
        cir.setReturnValue(this.recipes.getRecipesFor(type, input, level).filter(recipe -> recipe != found && RecipeMatching.allows(level, recipe)).findFirst());
    }

    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeHolder;)Ljava/util/Optional;",
            at = @At("RETURN"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void coolcatlib$matchingLast(RecipeType<T> type, I input, Level level, RecipeHolder<T> recipeHint,
                                                                                    CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir) {
        // Only the recipe used last skipped the question; the others were asked as they were looked up.
        if (recipeHint == null || cir.getReturnValue().orElse(null) != recipeHint || RecipeMatching.allows(level, recipeHint)) return;
        cir.setReturnValue(((RecipeManager) (Object) this).getRecipeFor(type, input, level));
    }
}
