package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.api.event.v2.common.BlockEvents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// BlockEvents.PLACE: a block item is about to put its block in the level, whoever uses it. Not placing makes place()
// fail, so the item isn't used up.
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "placeBlock", at = @At("HEAD"), cancellable = true)
    private void coolcatlib$beforePlace(BlockPlaceContext context, BlockState state, CallbackInfoReturnable<Boolean> cir) {
        Level level = context.getLevel();
        if (level.isClientSide() || !BlockEvents.PLACE.hasListeners()) return;
        if (BlockEvents.PLACE.invoker().onPlace(level, context.getClickedPos(), state, context.getPlayer(), context.getItemInHand()).isInterrupt()) {
            cir.setReturnValue(false);
        }
    }
}
