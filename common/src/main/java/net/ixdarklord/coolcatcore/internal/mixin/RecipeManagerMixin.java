package net.ixdarklord.coolcatcore.internal.mixin;

import com.mojang.datafixers.util.Pair;
import net.ixdarklord.coolcatcore.internal.event.RecipeMatching;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Optional;

// RecipeEvents.MATCH: a recipe refused is passed over for the next that matches, both ways the game looks one up (among
// all of a type's recipes, and starting with the recipe it used last).
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {
    @Shadow
    protected abstract <C extends Container, T extends Recipe<C>> Map<ResourceLocation, T> byType(RecipeType<T> recipeType);

    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;",
            at = @At("RETURN"), cancellable = true)
    private <C extends Container, T extends Recipe<C>> void coolcatlib$matching(RecipeType<T> type, C container, Level level, CallbackInfoReturnable<Optional<T>> cir) {
        T found = cir.getReturnValue().orElse(null);
        if (found == null || RecipeMatching.allows(level, found)) return;
        cir.setReturnValue(this.byType(type).values().stream()
                .filter(recipe -> recipe != found && recipe.matches(container, level) && RecipeMatching.allows(level, recipe)).findFirst());
    }

    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;",
            at = @At("RETURN"), cancellable = true)
    private <C extends Container, T extends Recipe<C>> void coolcatlib$matchingLast(RecipeType<T> type, C container, Level level, @Nullable ResourceLocation lastRecipe,
                                                                                    CallbackInfoReturnable<Optional<Pair<ResourceLocation, T>>> cir) {
        Pair<ResourceLocation, T> found = cir.getReturnValue().orElse(null);
        if (found == null || RecipeMatching.allows(level, found.getSecond())) return;
        cir.setReturnValue(this.byType(type).entrySet().stream()
                .filter(entry -> entry.getValue() != found.getSecond() && entry.getValue().matches(container, level) && RecipeMatching.allows(level, entry.getValue()))
                .findFirst().map(entry -> Pair.of(entry.getKey(), entry.getValue())));
    }
}
