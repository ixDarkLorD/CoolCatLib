package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.event.LootRolls;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

// LootEvents.MODIFY: the end of a roll LootTableMixin collects. Priority 1100, so it runs after other mods' own ends of
// the roll (at the default 1000): a mod that captured the loot hands it on there first (CraftTweaker on Fabric, its loot
// modifiers applied), and the event sees those items too.
@Mixin(value = LootTable.class, priority = 1100)
public abstract class LootTableEndMixin {
    @Inject(method = "getRandomItemsRaw(Lnet/minecraft/world/level/storage/loot/LootContext;Ljava/util/function/Consumer;)V", at = @At("RETURN"))
    private void coolcatlib$rolled(LootContext context, Consumer<ItemStack> output, CallbackInfo ci) {
        LootRolls.end((LootTable) (Object) this, context);
    }
}
