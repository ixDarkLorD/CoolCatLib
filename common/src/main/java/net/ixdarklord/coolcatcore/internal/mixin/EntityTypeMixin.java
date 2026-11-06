package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.event.SpawnTracking;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

// EntityEvents.SPAWN: a mob made in place for a reason (a spawn egg, a bucket, a command) remembers it.
@Mixin(EntityType.class)
public abstract class EntityTypeMixin {
    @Inject(method = "create(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/nbt/CompoundTag;Ljava/util/function/Consumer;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/MobSpawnType;ZZ)Lnet/minecraft/world/entity/Entity;", at = @At("RETURN"))
    private void coolcatlib$made(ServerLevel level, @Nullable CompoundTag nbt, @Nullable Consumer<?> consumer, BlockPos pos, MobSpawnType spawnType,
                                  boolean shouldOffsetY, boolean shouldOffsetYMore, CallbackInfoReturnable<Entity> cir) {
        if (cir.getReturnValue() != null) ((SpawnTracking.Tracked) cir.getReturnValue()).coolcatlib$setSpawnReason(spawnType);
    }
}
