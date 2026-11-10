package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.event.RecipeMatching;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeCache;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

// RecipeEvents.MATCH isn't asked for the crafters' recipe cache, which one answer serves for every crafter; their
// recipes go through RecipeEvents.CRAFT_RESULT instead.
@Mixin(RecipeCache.class)
public abstract class RecipeCacheMixin {
    @Inject(method = "get", at = @At("HEAD"))
    private void coolcatlib$enter(ServerLevel level, CraftingInput input, CallbackInfoReturnable<Optional<RecipeHolder<CraftingRecipe>>> cir) {
        RecipeMatching.enterUnasked();
    }

    @Inject(method = "get", at = @At("RETURN"))
    private void coolcatlib$exit(ServerLevel level, CraftingInput input, CallbackInfoReturnable<Optional<RecipeHolder<CraftingRecipe>>> cir) {
        RecipeMatching.exit();
    }
}
