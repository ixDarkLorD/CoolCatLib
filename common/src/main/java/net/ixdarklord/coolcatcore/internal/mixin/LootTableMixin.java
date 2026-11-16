package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.event.LootRolls;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiFunction;
import java.util.function.Consumer;

// LootEvents.MODIFY: every roll of a table comes through here (the lists and the consumers alike), and so do the tables
// rolled inside it.
//
// The loot is collected from the output the table's functions are given, not the method's own argument, and the
// mixin is applied early (priority 500): a mod that also wraps the output at the start and releases it at the end (as
// CraftTweaker's loot modifiers do on Fabric) then sits outside this collector, and gets the loot once this has passed
// it on. Collecting from the argument instead put this inside such a wrapper, which released its loot into this
// collector only after it had finished, and the loot was lost.
@Mixin(value = LootTable.class, priority = 500)
public abstract class LootTableMixin {
    @ModifyArg(method = "getRandomItemsRaw(Lnet/minecraft/world/level/storage/loot/LootContext;Ljava/util/function/Consumer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/loot/functions/LootItemFunction;decorate(Ljava/util/function/BiFunction;Ljava/util/function/Consumer;Lnet/minecraft/world/level/storage/loot/LootContext;)Ljava/util/function/Consumer;"),
            index = 1)
    private Consumer<ItemStack> coolcatlib$collect(BiFunction<ItemStack, LootContext, ItemStack> functions, Consumer<ItemStack> output, LootContext context) {
        return LootRolls.begin((LootTable) (Object) this, context, output);
    }

    @Inject(method = "getRandomItemsRaw(Lnet/minecraft/world/level/storage/loot/LootContext;Ljava/util/function/Consumer;)V", at = @At("RETURN"))
    private void coolcatlib$rolled(LootContext context, Consumer<ItemStack> output, CallbackInfo ci) {
        LootRolls.end((LootTable) (Object) this, context);
    }
}
