package net.ixdarklord.coolcatcanvas.api.client.cutscene;

import net.ixdarklord.coolcatcanvas.api.cutscene.CameraPose;
import net.ixdarklord.coolcatcanvas.api.cutscene.Cutscene;
import net.ixdarklord.coolcatcanvas.api.cutscene.CutsceneEndReason;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * A cutscene as it plays: what {@link Cutscenes#play} hands back. One plays at a time; starting another ends it.
 * <pre>{@code
 * CutscenePlayback playback = Cutscenes.play(MyMod.id("intro"));
 * if (playback != null) playback.onEnd(reason -> { if (reason.completed()) showTitle(); });
 * }</pre>
 */
public interface CutscenePlayback {
    Cutscene cutscene();

    /** The id it was started by, or null for one played straight from a {@link Cutscene}. */
    @Nullable Identifier id();

    /** How far in it is, in ticks. */
    float time();

    /** Its time as a fraction of its length, 0 to 1. */
    default float progress() {
        return Math.min(1.0F, this.time() / this.cutscene().duration());
    }

    /** Whether it is still the cutscene on screen: false once it has ended, for any reason. */
    boolean isActive();

    boolean isPaused();

    /** Holds it on its current frame, the camera staying where it is. */
    void pause();

    void resume();

    /** Jumps to a time, in ticks. Markers jumped over don't fire. */
    void seek(float time);

    float speed();

    /** How fast it runs: 1 is its own pace, 0.5 slow motion, 2 double time. */
    void setSpeed(float speed);

    /** Ends it at once, as {@link CutsceneEndReason#STOPPED}. */
    void stop();

    /** Ends it at once as if the player had skipped it, whether or not it is skippable. */
    void skip();

    /** Runs once it has ended, with the reason. Called at once if it already has. */
    CutscenePlayback onEnd(Consumer<CutsceneEndReason> listener);

    /** The camera this frame, in the world: where the cutscene has it, or would have it while it is paused. */
    CameraPose pose();
}
