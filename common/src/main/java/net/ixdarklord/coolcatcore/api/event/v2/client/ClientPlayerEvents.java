package net.ixdarklord.coolcatcore.api.event.v2.client;

import net.ixdarklord.coolcatcore.api.event.v2.core.EventInvoker;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * The local player joining and leaving a world or server.
 */
public final class ClientPlayerEvents {
    public static final EventInvoker<Join> JOIN = EventInvoker.create(Join.class, listeners -> player -> {
        for (Join listener : listeners) listener.onJoin(player);
    });
    public static final EventInvoker<Leave> LEAVE = EventInvoker.create(Leave.class, listeners -> player -> {
        for (Leave listener : listeners) listener.onLeave(player);
    });

    private ClientPlayerEvents() {}

    @FunctionalInterface
    public interface Join {
        void onJoin(LocalPlayer player);
    }

    /** The player may already be gone (e.g. the connection failed before joining). */
    @FunctionalInterface
    public interface Leave {
        void onLeave(@Nullable LocalPlayer player);
    }
}
