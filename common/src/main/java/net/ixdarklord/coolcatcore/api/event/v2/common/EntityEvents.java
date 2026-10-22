package net.ixdarklord.coolcatcore.api.event.v2.common;

import net.ixdarklord.coolcatcore.api.event.v2.core.EventInvoker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

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
}
