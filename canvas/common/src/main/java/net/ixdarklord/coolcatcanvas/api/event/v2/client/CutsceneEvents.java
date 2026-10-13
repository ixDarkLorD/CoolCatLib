package net.ixdarklord.coolcatcanvas.api.event.v2.client;

import net.ixdarklord.coolcatcanvas.api.client.cutscene.CutscenePlayback;
import net.ixdarklord.coolcatcanvas.api.cutscene.CameraKeyframe;
import net.ixdarklord.coolcatcanvas.api.cutscene.CameraPose;
import net.ixdarklord.coolcatcanvas.api.cutscene.Cutscene;
import net.ixdarklord.coolcatcanvas.api.cutscene.CutsceneEndReason;
import net.ixdarklord.coolcatcore.api.event.v2.core.EventInvoker;
import net.ixdarklord.coolcatcore.api.event.v2.core.EventResult;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Cutscenes playing on this client, whoever started them: a mod, the server, a command. All fire on the client's main
 * thread; {@link #CAMERA} fires every frame, the rest on ticks. Previews in the cutscene editor fire {@link #MARKER}
 * only.
 * <pre>{@code
 * CutsceneEvents.STARTED.register(playback -> music.duck());
 * CutsceneEvents.KEYFRAME.register((playback, index, keyframe) -> { if (index == 2) roar(); });
 * CutsceneEvents.ENDED.register((playback, reason) -> music.restore());
 * }</pre>
 * A listener is told about every cutscene; tell yours apart by {@link CutscenePlayback#id()}.
 */
public final class CutsceneEvents {
    /**
     * Before a cutscene starts, from any source. Returning {@link EventResult#INTERRUPT} keeps it from playing:
     * whatever was on screen stays, and a server that asked for it is told it was stopped.
     */
    public static final EventInvoker<BeforeStart> BEFORE_START = EventInvoker.create(BeforeStart.class, listeners -> (id, cutscene, fromServer) -> {
        for (BeforeStart listener : listeners) {
            EventResult result = listener.onBeforeStart(id, cutscene, fromServer);
            if (result.isInterrupt()) return result;
        }
        return EventResult.PASS;
    });
    /** After a cutscene started playing. */
    public static final EventInvoker<Started> STARTED = EventInvoker.create(Started.class, listeners -> playback -> {
        for (Started listener : listeners) listener.onStarted(playback);
    });
    /**
     * Every tick a cutscene's clock moves, after it has: not while it or the game is paused. The place for anything
     * that should run alongside it.
     */
    public static final EventInvoker<Tick> TICK = EventInvoker.create(Tick.class, listeners -> playback -> {
        for (Tick listener : listeners) listener.onTick(playback);
    });
    /**
     * As a cutscene's camera reaches one of its keyframes, the first included. Keyframes jumped over by a seek or a
     * skip don't fire.
     */
    public static final EventInvoker<Keyframe> KEYFRAME = EventInvoker.create(Keyframe.class, listeners -> (playback, index, keyframe) -> {
        for (Keyframe listener : listeners) listener.onKeyframe(playback, index, keyframe);
    });
    /**
     * As a cutscene passes one of its markers: the cue for a sound, an effect, a shake. Markers jumped over by a seek
     * or a skip don't fire.
     */
    public static final EventInvoker<Marker> MARKER = EventInvoker.create(Marker.class, listeners -> (playback, marker) -> {
        for (Marker listener : listeners) listener.onMarker(playback, marker);
    });
    /** As one of a cutscene's subtitles appears: for a voice line that goes with it. */
    public static final EventInvoker<Subtitle> SUBTITLE = EventInvoker.create(Subtitle.class, listeners -> (playback, subtitle) -> {
        for (Subtitle listener : listeners) listener.onSubtitle(playback, subtitle);
    });
    /** After a cutscene was paused or resumed through its {@link CutscenePlayback}. Pausing the game doesn't fire it. */
    public static final EventInvoker<Paused> PAUSED = EventInvoker.create(Paused.class, listeners -> (playback, paused) -> {
        for (Paused listener : listeners) listener.onPaused(playback, paused);
    });
    /**
     * Every frame, with the camera a cutscene is about to use: listeners may hand back another, each seeing what the
     * one before returned. For a camera that reacts to the world: nudged by an explosion, kept out of a wall.
     */
    public static final EventInvoker<Camera> CAMERA = EventInvoker.create(Camera.class, listeners -> (playback, pose, partialTick) -> {
        for (Camera listener : listeners) pose = listener.onCamera(playback, pose, partialTick);
        return pose;
    });
    /**
     * Before the player skips a cutscene by holding the skip key. Returning {@link EventResult#INTERRUPT} keeps it
     * playing. Not fired for {@link CutscenePlayback#skip()}, nor for cutscenes that aren't skippable.
     */
    public static final EventInvoker<BeforeSkip> BEFORE_SKIP = EventInvoker.create(BeforeSkip.class, listeners -> playback -> {
        for (BeforeSkip listener : listeners) {
            EventResult result = listener.onBeforeSkip(playback);
            if (result.isInterrupt()) return result;
        }
        return EventResult.PASS;
    });
    /**
     * After a cutscene ended, for any reason: seen through, skipped, stopped, replaced by another, or cut short by
     * leaving the world. The camera is the player's again.
     */
    public static final EventInvoker<Ended> ENDED = EventInvoker.create(Ended.class, listeners -> (playback, reason) -> {
        for (Ended listener : listeners) listener.onEnded(playback, reason);
    });

    private CutsceneEvents() {}

    @FunctionalInterface
    public interface BeforeStart {
        /**
         * @param id         the id it is played by, or null for one played straight from a {@link Cutscene}
         * @param fromServer whether the server asked for it
         */
        EventResult onBeforeStart(@Nullable Identifier id, Cutscene cutscene, boolean fromServer);
    }

    @FunctionalInterface
    public interface Started {
        void onStarted(CutscenePlayback playback);
    }

    @FunctionalInterface
    public interface Tick {
        void onTick(CutscenePlayback playback);
    }

    @FunctionalInterface
    public interface Keyframe {
        /**
         * @param index the keyframe's place in {@link Cutscene#keyframes()}, from 0
         */
        void onKeyframe(CutscenePlayback playback, int index, CameraKeyframe keyframe);
    }

    @FunctionalInterface
    public interface Marker {
        void onMarker(CutscenePlayback playback, Identifier marker);
    }

    @FunctionalInterface
    public interface Subtitle {
        void onSubtitle(CutscenePlayback playback, Cutscene.Subtitle subtitle);
    }

    @FunctionalInterface
    public interface Paused {
        void onPaused(CutscenePlayback playback, boolean paused);
    }

    @FunctionalInterface
    public interface Camera {
        /**
         * @param pose the camera in the world, after the cutscene's focuses and any listener before this one
         * @return the camera to use; {@code pose} to leave it alone
         */
        CameraPose onCamera(CutscenePlayback playback, CameraPose pose, float partialTick);
    }

    @FunctionalInterface
    public interface BeforeSkip {
        EventResult onBeforeSkip(CutscenePlayback playback);
    }

    @FunctionalInterface
    public interface Ended {
        void onEnded(CutscenePlayback playback, CutsceneEndReason reason);
    }
}
