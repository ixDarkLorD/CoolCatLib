package net.ixdarklord.coolcatcore.api.event.v2.client;

import net.ixdarklord.coolcatcore.api.event.v2.core.EventInvoker;
import net.minecraft.client.Minecraft;

/**
 * The client's tick.
 */
public final class ClientTickEvents {
    public static final EventInvoker<Start> START = EventInvoker.create(Start.class, listeners -> minecraft -> {
        for (Start listener : listeners) listener.onStartTick(minecraft);
    });
    public static final EventInvoker<End> END = EventInvoker.create(End.class, listeners -> minecraft -> {
        for (End listener : listeners) listener.onEndTick(minecraft);
    });

    private ClientTickEvents() {}

    @FunctionalInterface
    public interface Start {
        void onStartTick(Minecraft minecraft);
    }

    @FunctionalInterface
    public interface End {
        void onEndTick(Minecraft minecraft);
    }
}
