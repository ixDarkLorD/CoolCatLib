package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.event.SpawnTracking;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// EntityEvents.SPAWN: a mob set up for a reason (most spawns are, before they're added) remembers it, and one a mob
// turned into (a zombie villager cured, a pig struck by lightning) is a conversion.
@Mixin(Mob.class)
public abstract class MobMixin {
    @Inject(method = "finalizeSpawn(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/DifficultyInstance;Lnet/minecraft/world/entity/MobSpawnType;Lnet/minecraft/world/entity/SpawnGroupData;)Lnet/minecraft/world/entity/SpawnGroupData;", at = @At("HEAD"))
    private void coolcatlib$setUp(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                       @Nullable SpawnGroupData spawnData, CallbackInfoReturnable<SpawnGroupData> cir) {
        ((SpawnTracking.Tracked) this).coolcatlib$setSpawnReason(spawnType);
    }

    @ModifyArg(method = "convertTo", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private Entity coolcatlib$converted(Entity converted) {
        ((SpawnTracking.Tracked) converted).coolcatlib$setSpawnReason(MobSpawnType.CONVERSION);
        return converted;
    }
}
