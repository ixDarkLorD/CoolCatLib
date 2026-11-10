package net.ixdarklord.coolcatcore.internal.event;

import net.ixdarklord.coolcatcore.api.event.v2.common.EntityEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

/**
 * EntityEvents.SPAWN: a mob made by its EntityType remembers why (EntityTypeMixin), and is asked about when it's first
 * added to a level (ServerLevelMixin, WorldGenRegionMixin). Every entity carries this through EntityMixin.
 */
public final class SpawnTracking {
    private SpawnTracking() {}

    /** Whether a newly made entity must stay out of the level: refused itself, or riding something that was. */
    public static boolean refuse(Entity entity, ServerLevelAccessor level) {
        if (((Tracked) entity.getRootVehicle()).coolcatlib$isSpawnRefused()) return true;
        EntitySpawnReason reason = ((Tracked) entity).coolcatlib$takeSpawnReason();
        if (reason == null || !(entity instanceof Mob mob) || !EntityEvents.SPAWN.hasListeners()) return false;
        if (!EntityEvents.SPAWN.invoker().onSpawn(mob, level, reason).isInterrupt()) return false;
        ((Tracked) entity).coolcatlib$refuseSpawn();
        // A refused passenger mustn't stay on a vehicle that is added.
        if (entity.isPassenger()) entity.removeVehicle();
        return true;
    }

    /** Implemented by every entity. */
    public interface Tracked {
        void coolcatlib$setSpawnReason(EntitySpawnReason reason);

        /** The reason it was made for, once: null after the first call, or if it wasn't made new. */
        @Nullable EntitySpawnReason coolcatlib$takeSpawnReason();

        void coolcatlib$refuseSpawn();

        boolean coolcatlib$isSpawnRefused();
    }
}
