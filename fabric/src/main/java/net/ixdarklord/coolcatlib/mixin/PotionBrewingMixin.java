package net.ixdarklord.coolcatlib.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.ixdarklord.coolcatlib.api.brewing.BrewingRecipe;
import net.ixdarklord.coolcatlib.api.brewing.BrewingRecipeRegistry;
import net.ixdarklord.coolcatlib.api.brewing.IBrewingRecipe;
import net.ixdarklord.coolcatlib.api.event.v1.server.RegisterBrewingRecipesEvent;
import net.ixdarklord.coolcatlib.api.brewing.fabric.ext.PotionBrewingBuilderExt;
import net.ixdarklord.coolcatlib.api.brewing.fabric.ext.PotionBrewingExt;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(PotionBrewing.class)
public abstract class PotionBrewingMixin implements PotionBrewingExt {
    @Shadow
    protected abstract boolean isContainer(ItemStack itemStack);

    @Unique
    private BrewingRecipeRegistry coolcatlib$registry;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void createEmptyRegistry(final CallbackInfo ci) {
        this.coolcatlib$registry = new BrewingRecipeRegistry(List.of()); // Create an empty builder in case a mod doesn't use the builder
    }

    @Override
    public boolean isInput(ItemStack stack) {
        return this.coolcatlib$registry.isValidInput(stack) || isContainer(stack);
    }

    @Override
    public List<IBrewingRecipe> getRecipes() {
        return coolcatlib$registry.recipes();
    }

    @Override
    public void setBrewingRegistry(BrewingRecipeRegistry registry) {
        this.coolcatlib$registry = registry;
    }

    @Inject(method = "hasMix", at = @At("HEAD"), cancellable = true)
    private void checkMixRegistry(ItemStack container, ItemStack mix, CallbackInfoReturnable<Boolean> cir) {
        if (coolcatlib$registry.hasOutput(container, mix)) cir.setReturnValue(true);
    }

    @Inject(method = "mix", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;getOrDefault(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;"
    ), cancellable = true)
    private void doMix(ItemStack itemStack, ItemStack itemStack2, CallbackInfoReturnable<ItemStack> cir) {
        var customMix = coolcatlib$registry.getOutput(itemStack2, itemStack); // Parameters are swapped compared to what vanilla passes!
        if (!customMix.isEmpty()) cir.setReturnValue(customMix);
    }

    @Inject(method = "bootstrap", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/alchemy/PotionBrewing;addVanillaMixes(Lnet/minecraft/world/item/alchemy/PotionBrewing$Builder;)V",
            shift = At.Shift.AFTER
    ))
    private static void fireRegisterEvent(FeatureFlagSet featureFlagSet, CallbackInfoReturnable<PotionBrewing> cir, @Local PotionBrewing.Builder builder) {
        RegisterBrewingRecipesEvent event = RegisterBrewingRecipesEvent.invokeEvent(builder);
        for (IBrewingRecipe recipe : event.getBuilder().getBrewingRecipes()) {
            ((PotionBrewingBuilderExt) builder).addRecipe(recipe);
        }
    }

    @Inject(method = "isIngredient", at = @At("HEAD"), cancellable = true)
    private void checkRegistryForValidIngredient(ItemStack itemStack, CallbackInfoReturnable<Boolean> cir) {
        if (this.coolcatlib$registry.isValidIngredient(itemStack))
            cir.setReturnValue(true);
    }

    @Mixin(value = PotionBrewing.Builder.class, priority = 300)
    public static class BuilderMixin implements PotionBrewingBuilderExt {
        private final List<IBrewingRecipe> coolcatlib$recipes = new ArrayList<>();

        @Override
        public void addRecipe(Ingredient input, Ingredient ingredient, ItemStack output) {
            addRecipe(new BrewingRecipe(input, ingredient, output));
        }

        @Override
        public void addRecipe(IBrewingRecipe recipe) {
            this.coolcatlib$recipes.add(recipe);
        }

        @ModifyReturnValue(method = "build", at = @At("RETURN"))
        private PotionBrewing addCustomRecipes(PotionBrewing original) {
            ((PotionBrewingExt) original).setBrewingRegistry(new BrewingRecipeRegistry(coolcatlib$recipes));
            return original;
        }
    }
}