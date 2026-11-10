package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.api.event.v2.common.RecipeEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

// RecipeEvents.MATCH for the recipe picked at a stonecutter (which picks from a list rather than looking one up): a
// refused one leaves the result slot empty.
@Mixin(StonecutterMenu.class)
public abstract class StonecutterMenuMixin {
    @Shadow
    @Final
    private Level level;
    @Unique
    private Player coolcatlib$player;

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("RETURN"))
    private void coolcatlib$rememberPlayer(int containerId, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
        this.coolcatlib$player = inventory.player;
    }

    @ModifyVariable(method = "setupResultSlot", at = @At(value = "LOAD", ordinal = 0), ordinal = 0)
    private Optional<RecipeHolder<StonecutterRecipe>> coolcatlib$match(Optional<RecipeHolder<StonecutterRecipe>> recipe) {
        if (recipe.isEmpty() || !(this.level instanceof ServerLevel serverLevel) || !RecipeEvents.MATCH.hasListeners()) return recipe;
        return RecipeEvents.MATCH.invoker().onMatch(serverLevel, recipe.get(), null, this.coolcatlib$player).isInterrupt() ? Optional.empty() : recipe;
    }
}
