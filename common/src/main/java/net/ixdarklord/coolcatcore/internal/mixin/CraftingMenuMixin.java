package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.api.event.v2.common.RecipeEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// RecipeEvents.CRAFT_RESULT for a crafting grid (the crafting table's, and the inventory's, which shares this code):
// the result is changed before it goes in the slot and to the client.
@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {
    // (At the result's first read, which is for the slot; the client is sent the same changed result after.)
    @ModifyVariable(method = "slotChangedCraftingGrid", ordinal = 0, at = @At(value = "LOAD", ordinal = 0))
    private static ItemStack coolcatlib$craftResult(ItemStack result, AbstractContainerMenu menu, Level level, Player player,
                                                    CraftingContainer container, ResultContainer resultSlots) {
        Recipe<?> recipe = resultSlots.getRecipeUsed();
        if (result.isEmpty() || recipe == null || level.isClientSide() || !RecipeEvents.CRAFT_RESULT.hasListeners()) return result;
        return RecipeEvents.CRAFT_RESULT.invoker().onCraftResult(player, recipe, container, result).getInterrupt().orElse(result);
    }
}
