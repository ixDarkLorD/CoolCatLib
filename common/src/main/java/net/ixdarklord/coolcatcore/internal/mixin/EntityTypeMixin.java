package net.ixdarklord.coolcatcore.internal.mixin;

import net.ixdarklord.coolcatcore.internal.event.SpawnTracking;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// EntityEvents.SPAWN: a mob made on the server remembers why, until it's added to a level. Every spawn makes its mob
// here, and so does loading one (which isn't a spawn, and isn't added as a new entity).
@Mixin(EntityType.class)
public abstract class EntityTypeMixin {
    @Inject(method = "create(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/EntitySpawnReason;)Lnet/minecraft/world/entity/Entity;", at = @At("RETURN"))
    private void coolcatlib$made(Level level, EntitySpawnReason reason, CallbackInfoReturnable<Entity> cir) {
        if (cir.getReturnValue() instanceof Mob mob && !level.isClientSide()
                && reason != EntitySpawnReason.LOAD && reason != EntitySpawnReason.DIMENSION_TRAVEL) {
            ((SpawnTracking.Tracked) mob).coolcatlib$setSpawnReason(reason);
        }
    }
}
