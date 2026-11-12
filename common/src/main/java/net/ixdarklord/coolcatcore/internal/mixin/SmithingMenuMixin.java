package net.ixdarklord.coolcatcore.internal.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ixdarklord.coolcatcore.internal.event.RecipeMatching;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

// RecipeEvents.MATCH: the player at a smithing table is who looks its recipes up; refused ones aren't offered. Wrapped
// rather than redirected, so other mods can change the same lookup too (Citadel redirects it).
@Mixin(SmithingMenu.class)
public abstract class SmithingMenuMixin extends ItemCombinerMenu {
    private SmithingMenuMixin(@Nullable MenuType<?> type, int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(type, containerId, inventory, access);
    }

    @WrapOperation(method = "createResult", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/RecipeManager;getRecipesFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;)Ljava/util/List;"))
    private <C extends Container, T extends Recipe<C>> List<T> coolcatlib$matching(RecipeManager recipes, RecipeType<T> type, C container, Level level,
                                                                                   Operation<List<T>> lookup) {
        RecipeMatching.enter(null, this.player);
        try {
            return lookup.call(recipes, type, container, level).stream().filter(recipe -> RecipeMatching.allows(level, recipe)).toList();
        } finally {
            RecipeMatching.exit();
        }
    }
}
