package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.event.RecipeMatching;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.Optional;

// RecipeEvents.MATCH: a recipe refused is passed over for the next that matches. Every way the game looks one up comes
// here (starting with the recipe it used last, if any).
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {
    @Shadow
    protected abstract <I extends RecipeInput, T extends Recipe<I>> Collection<RecipeHolder<T>> byType(RecipeType<T> type);

    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeHolder;)Ljava/util/Optional;",
            at = @At("RETURN"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void coolcatlib$matching(RecipeType<T> type, I input, Level level, @Nullable RecipeHolder<T> lastRecipe,
                                                                                  CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir) {
        RecipeHolder<T> found = cir.getReturnValue().orElse(null);
        if (found == null || RecipeMatching.allows(level, found)) return;
        cir.setReturnValue(this.<I, T>byType(type).stream()
                .filter(recipe -> recipe != found && recipe.value().matches(input, level) && RecipeMatching.allows(level, recipe)).findFirst());
    }
}
