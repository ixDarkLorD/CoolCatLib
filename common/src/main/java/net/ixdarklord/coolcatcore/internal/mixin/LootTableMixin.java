package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.event.LootRolls;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Consumer;

// LootEvents.MODIFY: every roll of a table comes through here (the lists and the consumers alike), and so do the tables
// rolled inside it. The roll is collected from here and ended in LootTableEndMixin.
//
// Priority 900, so this wrapping goes on before other mods' (applied at the default 1000) and ends up inside them. A mod
// that captures the loot and hands it on only if its own wrapper is the outer one (CraftTweaker on Fabric, for its loot
// modifiers) then still hands it on, here; otherwise the roll's items were lost (blocks dropped nothing).
@Mixin(value = LootTable.class, priority = 900)
public abstract class LootTableMixin {
    @ModifyVariable(method = "getRandomItemsRaw(Lnet/minecraft/world/level/storage/loot/LootContext;Ljava/util/function/Consumer;)V", at = @At("HEAD"), argsOnly = true)
    private Consumer<ItemStack> coolcatlib$collect(Consumer<ItemStack> output, LootContext context) {
        return LootRolls.begin((LootTable) (Object) this, context, output);
    }
}
