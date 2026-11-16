package net.ixdarklord.coolcatcanvas.api.event.v2.common;

import net.ixdarklord.coolcatcanvas.api.cutscene.CutsceneEndReason;
import net.ixdarklord.coolcatcore.api.event.v2.core.EventInvoker;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Cutscenes the server started with {@code CutsceneControl}, as their players watch them. All fire on the server
 * thread. Cutscenes a client plays on its own are not reported.
 * <pre>{@code
 * ServerCutsceneEvents.KEYFRAME.register((player, cutscene, index) -> {
 *     if (cutscene.equals(MyMod.id("boss_arena")) && index == 2) boss.roar();
 * });
 * ServerCutsceneEvents.ENDED.register((player, cutscene, reason) -> arena.begin(player));
 * }</pre>
 * {@link #KEYFRAME}, {@link #MARKER} and {@link #ENDED} are what the player's client reports: they arrive a little
 * after the fact, and a modified client can claim any of them at any time, for the cutscene it was sent. Don't hand
 * out anything of value on their word alone. {@link #STARTED} and {@link #TICK} are the server's own.
 */
public final class ServerCutsceneEvents {
    /** After a player was sent a cutscene to play. */
    public static final EventInvoker<Started> STARTED = EventInvoker.create(Started.class, listeners -> (player, cutscene) -> {
        for (Started listener : listeners) listener.onStarted(player, cutscene);
    });
    /**
     * Every server tick a player is watching a cutscene, from the tick after it was sent until it ends. The count is
     * the server's own: the player's clock can differ (lag, a paused or slowed playback), so use {@link #KEYFRAME}
     * or {@link #MARKER} for anything that must land on a moment of the scene.
     */
    public static final EventInvoker<Tick> TICK = EventInvoker.create(Tick.class, listeners -> (player, cutscene, ticks) -> {
        for (Tick listener : listeners) listener.onTick(player, cutscene, ticks);
    });
    /** As a player's camera reaches one of the cutscene's keyframes, the first included. */
    public static final EventInvoker<Keyframe> KEYFRAME = EventInvoker.create(Keyframe.class, listeners -> (player, cutscene, index) -> {
        for (Keyframe listener : listeners) listener.onKeyframe(player, cutscene, index);
    });
    /** As a player's cutscene passes one of its markers. */
    public static final EventInvoker<Marker> MARKER = EventInvoker.create(Marker.class, listeners -> (player, cutscene, marker) -> {
        for (Marker listener : listeners) listener.onMarker(player, cutscene, marker);
    });
    /**
     * After a player's cutscene ended: seen through, skipped, stopped, replaced by another, or cut short by them
     * leaving. The place to hand the world back: teleport them, start the fight, lift what kept them safe.
     */
    public static final EventInvoker<Ended> ENDED = EventInvoker.create(Ended.class, listeners -> (player, cutscene, reason) -> {
        for (Ended listener : listeners) listener.onEnded(player, cutscene, reason);
    });

    private ServerCutsceneEvents() {}

    @FunctionalInterface
    public interface Started {
        void onStarted(ServerPlayer player, Identifier cutscene);
    }

    @FunctionalInterface
    public interface Tick {
        /**
         * @param ticks how many server ticks the player has been watching, from 1
         */
        void onTick(ServerPlayer player, Identifier cutscene, int ticks);
    }

    @FunctionalInterface
    public interface Keyframe {
        /**
         * @param index the keyframe's place in the cutscene's keyframes, from 0, as the client reported it
         */
        void onKeyframe(ServerPlayer player, Identifier cutscene, int index);
    }

    @FunctionalInterface
    public interface Marker {
        void onMarker(ServerPlayer player, Identifier cutscene, Identifier marker);
    }

    @FunctionalInterface
    public interface Ended {
        /** The reason is what the player's client reported; a modified client can claim any. */
        void onEnded(ServerPlayer player, Identifier cutscene, CutsceneEndReason reason);
    }
}
