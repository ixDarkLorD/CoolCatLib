package net.ixdarklord.coolcatcore.api.event.v2.common;

import net.ixdarklord.coolcatcore.api.event.v2.core.EventInvoker;
import net.ixdarklord.coolcatcore.api.event.v2.core.EventResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * Entities on the server.
 */
public final class EntityEvents {
    public static final EventInvoker<Load> LOAD = EventInvoker.create(Load.class, listeners -> (entity, level) -> {
        for (Load listener : listeners) listener.onLoad(entity, level);
    });
    public static final EventInvoker<Unload> UNLOAD = EventInvoker.create(Unload.class, listeners -> (entity, level) -> {
        for (Unload listener : listeners) listener.onUnload(entity, level);
    });

    public static final EventInvoker<Spawn> SPAWN = EventInvoker.create(Spawn.class, listeners -> (mob, level, reason) -> {
        for (Spawn listener : listeners) {
            EventResult result = listener.onSpawn(mob, level, reason);
            if (result.isInterrupt()) return result;
        }
        return EventResult.PASS;
    });
    public static final EventInvoker<Mount> MOUNT = EventInvoker.create(Mount.class, listeners -> (rider, vehicle, mounting) -> {
        for (Mount listener : listeners) {
            EventResult result = listener.onMount(rider, vehicle, mounting);
            if (result.isInterrupt() && mounting) return result;
        }
        return EventResult.PASS;
    });

    private EntityEvents() {}

    /** An entity was added to a server level: spawned, or loaded with its chunk (players too, when they join). */
    @FunctionalInterface
    public interface Load {
        void onLoad(Entity entity, ServerLevel level);
    }

    /** An entity was taken out of a server level: killed, discarded, unloaded with its chunk, or gone to another dimension. */
    @FunctionalInterface
    public interface Unload {
        void onUnload(Entity entity, ServerLevel level);
    }

    /**
     * A new mob is about to be added to the world, already placed: spawned naturally or with the chunk's generation, by
     * a spawner or trial spawner, a spawn egg, a command, breeding, a conversion, a raid, and so on. Interrupting keeps it
     * out of the world, with its passengers (a spider jockey's skeleton). Mobs loaded with their chunk, or changing
     * dimension, aren't new and don't fire it.
     * <pre>{@code
     * EntityEvents.SPAWN.register((mob, level, reason) ->
     *         reason == EntitySpawnReason.NATURAL && isLockedNear(mob) ? EventResult.INTERRUPT : EventResult.PASS);
     * }</pre>
     *
     * @param level  where it's going: a {@link ServerLevel}, or the chunk being generated for
     *               {@link EntitySpawnReason#CHUNK_GENERATION}
     * @param reason how it was made
     */
    @FunctionalInterface
    public interface Spawn {
        EventResult onSpawn(Mob mob, ServerLevelAccessor level, EntitySpawnReason reason);
    }

    /**
     * An entity is about to start riding another ({@code mounting} true), or has stopped riding one ({@code mounting}
     * false), on the server. Interrupting a mount keeps it off; a dismount can't be stopped (the game counts on it in too
     * many places), so its result is ignored. Mounting while riding something else fires only the mount.
     */
    @FunctionalInterface
    public interface Mount {
        EventResult onMount(Entity rider, Entity vehicle, boolean mounting);
    }
}
