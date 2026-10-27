package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.event.RecipeMatching;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

// RecipeEvents.MATCH: the player at a smithing table is who looks its recipes up; refused ones aren't offered.
@Mixin(SmithingMenu.class)
public abstract class SmithingMenuMixin extends ItemCombinerMenu {
    private SmithingMenuMixin(@Nullable MenuType<?> type, int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(type, containerId, inventory, access);
    }

    @Redirect(method = "createResult", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/RecipeManager;getRecipesFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/List;"))
    private <I extends RecipeInput, T extends Recipe<I>> List<RecipeHolder<T>> coolcatlib$matching(RecipeManager recipes, RecipeType<T> type, I input, Level level) {
        RecipeMatching.enter(null, this.player);
        try {
            return recipes.getRecipesFor(type, input, level).stream().filter(recipe -> RecipeMatching.allows(level, recipe)).toList();
        } finally {
            RecipeMatching.exit();
        }
    }
}
