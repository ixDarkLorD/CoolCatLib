package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.event.SpawnTracking;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// EntityEvents.SPAWN for the mobs a new chunk is generated with.
@Mixin(WorldGenRegion.class)
public abstract class WorldGenRegionMixin {
    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void coolcatlib$spawn(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (SpawnTracking.refuse(entity, (WorldGenRegion) (Object) this)) cir.setReturnValue(false);
    }
}
