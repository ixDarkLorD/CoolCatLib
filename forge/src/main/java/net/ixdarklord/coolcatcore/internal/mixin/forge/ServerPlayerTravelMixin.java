package net.ixdarklord.coolcatcore.internal.mixin.forge;

import net.ixdarklord.coolcatcore.internal.event.DimensionTravelHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.util.ITeleporter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// PlayerEvents.TRAVEL_TO_DIMENSION before a player goes through a portal, or is moved by another mod's teleporter:
// Forge's changeDimension takes an ITeleporter, and vanilla's one-argument call leads here (teleportTo is the common
// ServerPlayerMixin's).
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTravelMixin {
    @Inject(method = "changeDimension(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraftforge/common/util/ITeleporter;)Lnet/minecraft/world/entity/Entity;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void coolcatlib$beforeTravel(ServerLevel destination, ITeleporter teleporter, CallbackInfoReturnable<Entity> cir) {
        if (!DimensionTravelHooks.mayTravel((ServerPlayer) (Object) this, destination)) cir.setReturnValue(null);
    }
}
