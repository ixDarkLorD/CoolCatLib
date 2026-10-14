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

    private EntityEvents() {}

    /** An entity was added to a server level: spawned, or loaded with its chunk (players too, when they join). */
    @FunctionalInterface
    public interface Load {
        void onLoad(Entity entity, ServerLevel level);
    }
}
