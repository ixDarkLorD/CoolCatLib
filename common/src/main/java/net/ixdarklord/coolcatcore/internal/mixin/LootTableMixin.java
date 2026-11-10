package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.event.LootRolls;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

// LootEvents.MODIFY: every roll of a table comes through here (the lists and the consumers alike), and so do the tables
// rolled inside it.
@Mixin(LootTable.class)
public abstract class LootTableMixin {
    @ModifyVariable(method = "getRandomItemsRaw(Lnet/minecraft/world/level/storage/loot/LootContext;Ljava/util/function/Consumer;)V", at = @At("HEAD"), argsOnly = true)
    private Consumer<ItemStack> coolcatlib$collect(Consumer<ItemStack> output, LootContext context) {
        return LootRolls.begin((LootTable) (Object) this, context, output);
    }

    @Inject(method = "getRandomItemsRaw(Lnet/minecraft/world/level/storage/loot/LootContext;Ljava/util/function/Consumer;)V", at = @At("RETURN"))
    private void coolcatlib$rolled(LootContext context, Consumer<ItemStack> output, CallbackInfo ci) {
        LootRolls.end((LootTable) (Object) this, context);
    }
}
