package net.ixdarklord.coolcatcore.api.event.v2.common;

import net.ixdarklord.coolcatcore.api.event.v2.core.EventInvoker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The server's lifecycle, for dedicated and integrated servers alike.
 */
public final class ServerLifecycleEvents {
    public static final EventInvoker<Starting> STARTING = EventInvoker.create(Starting.class, listeners -> server -> {
        for (Starting listener : listeners) listener.onServerStarting(server);
    });
    public static final EventInvoker<Started> STARTED = EventInvoker.create(Started.class, listeners -> server -> {
        for (Started listener : listeners) listener.onServerStarted(server);
    });
    public static final EventInvoker<Stopping> STOPPING = EventInvoker.create(Stopping.class, listeners -> server -> {
        for (Stopping listener : listeners) listener.onServerStopping(server);
    });
    public static final EventInvoker<Stopped> STOPPED = EventInvoker.create(Stopped.class, listeners -> server -> {
        for (Stopped listener : listeners) listener.onServerStopped(server);
    });
    public static final EventInvoker<SyncDataPackContents> SYNC_DATA_PACK_CONTENTS = EventInvoker.create(SyncDataPackContents.class, listeners -> (player, joined) -> {
        for (SyncDataPackContents listener : listeners) listener.onSyncDataPackContents(player, joined);
    });

    private ServerLifecycleEvents() {}

    /** Before the levels load. */
    @FunctionalInterface
    public interface Starting {
        void onServerStarting(MinecraftServer server);
    }

    /** Once the server is ready to tick. */
    @FunctionalInterface
    public interface Started {
        void onServerStarted(MinecraftServer server);
    }

    /** When the server begins shutting down, before the levels save. */
    @FunctionalInterface
    public interface Stopping {
        void onServerStopping(MinecraftServer server);
    }

    /** After the server has shut down. */
    @FunctionalInterface
    public interface Stopped {
        void onServerStopped(MinecraftServer server);
    }

    /**
     * When a player needs the server's datapack contents: once as they log in ({@code joined} is true), and for every
     * player after a datapack reload such as {@code /reload} ({@code joined} is false). Send data loaded from datapacks
     * (by {@code ReloadListeners}) to clients here.
     */
    @FunctionalInterface
    public interface SyncDataPackContents {
        void onSyncDataPackContents(ServerPlayer player, boolean joined);
    }
}
