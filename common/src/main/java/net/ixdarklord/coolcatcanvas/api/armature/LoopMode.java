package net.ixdarklord.coolcatcanvas.api.armature;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** What a clip does when it reaches its end. */
public enum LoopMode {
    /** Stops; its layer then goes back to what it was doing (its driver, the next motion, or rest). */
    ONCE,
    /** Starts over, forever or {@linkplain Motion#repeat(int) a number of times}. */
    LOOP,
    /** Stops on its last frame and stays there until something else plays. */
    HOLD,
    /** Plays to the end, then backwards to the start, and again. */
    PING_PONG;

    /** By name ({@code "loop"}, {@code "hold_on_last_frame"} and {@code "hold"} are the same); null if unknown. */
    public static @Nullable LoopMode byName(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "once", "false", "play_once" -> ONCE;
            case "loop", "true", "repeat" -> LOOP;
            case "hold", "hold_on_last_frame" -> HOLD;
            case "ping_pong", "pingpong", "bounce" -> PING_PONG;
            default -> null;
        };
    }
}
