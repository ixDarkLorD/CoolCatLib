package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.event.SpawnTracking;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// EntityEvents.SPAWN: a baby bred by two animals.
@Mixin(Animal.class)
public abstract class AnimalMixin {
    @ModifyArg(method = "spawnChildFromBreeding", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)V"))
    private Entity coolcatlib$bred(Entity baby) {
        ((SpawnTracking.Tracked) baby).coolcatlib$setSpawnReason(MobSpawnType.BREEDING);
        return baby;
    }
}
