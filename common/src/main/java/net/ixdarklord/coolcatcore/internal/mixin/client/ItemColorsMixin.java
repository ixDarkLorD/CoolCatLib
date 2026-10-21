package net.ixdarklord.coolcatcore.internal.mixin.client;

import net.ixdarklord.coolcatcore.internal.client.ItemColorHooks;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.item.ItemColors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// ItemColorRegistry: its colours go into the game's item colours as they are made.
@Mixin(ItemColors.class)
public abstract class ItemColorsMixin {
    @Inject(method = "createDefault", at = @At("RETURN"))
    private static void coolcatcore$registerColors(BlockColors blockColors, CallbackInfoReturnable<ItemColors> cir) {
        ItemColorHooks.onCreated(cir.getReturnValue());
    }
}
