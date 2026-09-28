package net.ixdarklord.coolcatcore.api.event.v2.common;

import net.ixdarklord.coolcatcore.api.event.v2.core.EventInvoker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * The server's tick, and each of its levels' ticks.
 */
public final class ServerTickEvents {
    public static final EventInvoker<Start> START = EventInvoker.create(Start.class, listeners -> server -> {
        for (Start listener : listeners) listener.onStartTick(server);
    });
    public static final EventInvoker<End> END = EventInvoker.create(End.class, listeners -> server -> {
        for (End listener : listeners) listener.onEndTick(server);
    });
    public static final EventInvoker<StartLevel> START_LEVEL = EventInvoker.create(StartLevel.class, listeners -> level -> {
        for (StartLevel listener : listeners) listener.onStartLevelTick(level);
    });
    public static final EventInvoker<EndLevel> END_LEVEL = EventInvoker.create(EndLevel.class, listeners -> level -> {
        for (EndLevel listener : listeners) listener.onEndLevelTick(level);
    });

    private ServerTickEvents() {}

    @FunctionalInterface
    public interface Start {
        void onStartTick(MinecraftServer server);
    }

    @FunctionalInterface
    public interface End {
        void onEndTick(MinecraftServer server);
    }

    @FunctionalInterface
    public interface StartLevel {
        void onStartLevelTick(ServerLevel level);
    }

    @FunctionalInterface
    public interface EndLevel {
        void onEndLevelTick(ServerLevel level);
    }
}
