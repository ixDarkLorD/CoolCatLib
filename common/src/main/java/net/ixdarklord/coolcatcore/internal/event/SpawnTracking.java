package net.ixdarklord.coolcatcore.internal.event;

import net.ixdarklord.coolcatcore.api.event.v2.common.EntityEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

/**
 * EntityEvents.SPAWN: a mob is asked about the first time it's added to a level as a new entity (ServerLevelMixin,
 * WorldGenRegionMixin), with the reason it was set up for (MobMixin, EntityTypeMixin, AnimalMixin), if the game gave
 * one. Every entity carries this through EntityMixin.
 */
public final class SpawnTracking {
    private SpawnTracking() {}

    /** Whether a new entity must stay out of the level: refused itself, or riding something that was. */
    public static boolean refuse(Entity entity, ServerLevelAccessor level) {
        if (((Tracked) entity.getRootVehicle()).coolcatlib$isSpawnRefused()) return true;
        Tracked tracked = (Tracked) entity;
        if (!(entity instanceof Mob mob) || tracked.coolcatlib$wasSpawnAsked() || !EntityEvents.SPAWN.hasListeners()) return false;
        tracked.coolcatlib$markSpawnAsked();
        if (!EntityEvents.SPAWN.invoker().onSpawn(mob, level, tracked.coolcatlib$getSpawnReason()).isInterrupt()) return false;
        tracked.coolcatlib$refuseSpawn();
        // A refused passenger mustn't stay on a vehicle that is added.
        if (entity.isPassenger()) entity.removeVehicle();
        return true;
    }

    /** Implemented by every entity. */
    public interface Tracked {
        void coolcatlib$setSpawnReason(MobSpawnType reason);

        @Nullable MobSpawnType coolcatlib$getSpawnReason();

        void coolcatlib$markSpawnAsked();

        boolean coolcatlib$wasSpawnAsked();

        void coolcatlib$refuseSpawn();

        boolean coolcatlib$isSpawnRefused();
    }
}
