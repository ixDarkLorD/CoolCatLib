package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.api.event.v2.common.RecipeEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// RecipeEvents.CRAFT_RESULT for a crafter about to craft; an empty result makes it fail as with no recipe.
@Mixin(CrafterBlock.class)
public abstract class CrafterBlockMixin {
    @ModifyVariable(method = "dispenseFrom", ordinal = 0,
            at = @At(value = "INVOKE_ASSIGN", target = "Lnet/minecraft/world/item/crafting/CraftingRecipe;assemble(Lnet/minecraft/world/item/crafting/RecipeInput;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack coolcatlib$craftResult(ItemStack result, BlockState state, ServerLevel level, BlockPos pos) {
        if (result.isEmpty() || !RecipeEvents.CRAFT_RESULT.hasListeners() || !(level.getBlockEntity(pos) instanceof CrafterBlockEntity crafter)) return result;
        CraftingInput input = crafter.asCraftInput();
        return CrafterBlock.getPotentialResults(level, input)
                .map(recipe -> RecipeEvents.CRAFT_RESULT.invoker().onCraftResult(null, recipe, input, result).getInterrupt().orElse(result))
                .orElse(result);
    }
}
