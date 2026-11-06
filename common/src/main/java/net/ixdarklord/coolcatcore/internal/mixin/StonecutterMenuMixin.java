package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.api.event.v2.common.RecipeEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// RecipeEvents.MATCH for the recipe picked at a stonecutter (which picks from a list rather than looking one up): a
// refused one leaves the result slot empty. The list itself isn't changed, since the client keeps its own copy.
@Mixin(StonecutterMenu.class)
public abstract class StonecutterMenuMixin {
    @Shadow
    @Final
    private Level level;
    @Shadow
    @Final
    private DataSlot selectedRecipeIndex;
    @Shadow
    private List<RecipeHolder<StonecutterRecipe>> recipes;
    @Shadow
    @Final
    Slot resultSlot;
    @Unique
    private Player coolcatlib$player;

    @Shadow
    protected abstract boolean isValidRecipeIndex(int recipeIndex);

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("RETURN"))
    private void coolcatlib$rememberPlayer(int containerId, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
        this.coolcatlib$player = inventory.player;
    }

    @Inject(method = "setupResultSlot", at = @At("HEAD"), cancellable = true)
    private void coolcatlib$match(CallbackInfo ci) {
        if (!(this.level instanceof ServerLevel serverLevel) || !RecipeEvents.MATCH.hasListeners()) return;
        int index = this.selectedRecipeIndex.get();
        if (this.recipes.isEmpty() || !this.isValidRecipeIndex(index)) return;
        if (RecipeEvents.MATCH.invoker().onMatch(serverLevel, this.recipes.get(index), null, this.coolcatlib$player).isInterrupt()) {
            this.resultSlot.set(ItemStack.EMPTY);
            ((AbstractContainerMenu) (Object) this).broadcastChanges();
            ci.cancel();
        }
    }
}
