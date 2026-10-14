package net.ixdarklord.coolcatcanvas.internal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

/**
 * The time world effects, skyboxes and screen effects animate by: real seconds, smooth from frame to frame, that
 * stop while the game is paused and go on from where they stopped when it is not. Everything Canvas draws that moves
 * by the clock reads it here (the {@code Time} of its shaders, the {@code time} and {@code deltaTime} it hands to
 * mods), so a paused game is a still picture: nothing drifts, pulses or scrolls behind the pause menu, and nothing
 * has jumped ahead when the game is taken up again.
 * <p>
 * A game is paused only where the game itself says so (a singleplayer world with a pausing screen open); on a
 * server, or with the world open to LAN, time goes on as the world does.
 */
public final class GameClock {
    private static long last = Util.getNanos();
    private static double seconds;

    private GameClock() {
    }

    /** Seconds the game has run unpaused since the client started. Client thread only. */
    public static double seconds() {
        long now = Util.getNanos();
        if (!Minecraft.getInstance().isPaused()) {
            // (A long gap is a hitch or a stopped debugger, not time anything should animate through.)
            seconds += Math.min(0.25, (now - last) / 1.0E9);
        }
        last = now;
        return seconds;
    }
}
