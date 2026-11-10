package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.event.LootRolls;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// LootEvents.MODIFY: a loot table looked up by its id, which the table itself doesn't know.
@Mixin(ReloadableServerRegistries.Holder.class)
public abstract class ReloadableServerRegistriesHolderMixin {
    @Inject(method = "getLootTable", at = @At("RETURN"))
    private void coolcatlib$rememberId(ResourceKey<LootTable> id, CallbackInfoReturnable<LootTable> cir) {
        LootRolls.remember(cir.getReturnValue(), id);
    }
}
