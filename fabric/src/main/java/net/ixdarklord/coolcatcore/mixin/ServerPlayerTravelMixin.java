package net.ixdarklord.coolcatcore.mixin;

import net.ixdarklord.coolcatcore.internal.event.DimensionTravelHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// PlayerEvents.TRAVEL_TO_DIMENSION before a player goes through a portal, or is moved by Fabric's or another mod's
// teleport (teleportTo is the common ServerPlayerMixin's).
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTravelMixin {
    @Inject(method = "changeDimension", at = @At("HEAD"), cancellable = true)
    private void coolcatlib$beforeTravel(ServerLevel destination, CallbackInfoReturnable<Entity> cir) {
        if (!DimensionTravelHooks.mayTravel((ServerPlayer) (Object) this, destination)) cir.setReturnValue(null);
    }
}
