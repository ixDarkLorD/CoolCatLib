package net.ixdarklord.coolcatcanvas.internal.client.cutscene;

import net.ixdarklord.coolcatcanvas.api.client.cutscene.CutscenePlayback;
import net.ixdarklord.coolcatcanvas.api.cutscene.CameraFocus;
import net.ixdarklord.coolcatcanvas.api.cutscene.CameraKeyframe;
import net.ixdarklord.coolcatcanvas.api.cutscene.CameraPose;
import net.ixdarklord.coolcatcanvas.api.cutscene.Cutscene;
import net.ixdarklord.coolcatcanvas.api.cutscene.CutsceneEndReason;
import net.ixdarklord.coolcatcanvas.api.event.v2.client.CutsceneEvents;
import net.ixdarklord.coolcatcanvas.api.utils.Easing;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.ixdarklord.coolcatcanvas.internal.cutscene.CutsceneProgressPayload;
import net.ixdarklord.coolcatcore.api.network.Network;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// One cutscene playing: its clock, where its space sits in the world, and the camera it gives for a frame. The
// editor's previews are playbacks too, with a cutscene that changes under them and a clock that stops at the end.
public final class PlaybackImpl implements CutscenePlayback {
    private Cutscene cutscene;
    private final @Nullable Identifier id;
    private Vec3 origin;
    private float originYaw;
    final boolean fromServer;
    final boolean preview;
    private final List<Consumer<CutsceneEndReason>> endListeners = new ArrayList<>();
    private float time;
    private float oldTime;
    // What lies at or before this time has been reported: keyframes, subtitles, markers.
    private float markersFired = -1.0F;
    private float speed = 1.0F;
    private boolean paused;
    private boolean cameraEnabled = true;
    private @Nullable CutsceneEndReason ended;
    int age;
    int skipHeld;

    PlaybackImpl(Cutscene cutscene, @Nullable Identifier id, Vec3 origin, float originYaw, boolean fromServer, boolean preview) {
        this.cutscene = cutscene;
        this.id = id;
        this.origin = origin;
        this.originYaw = originYaw;
        this.fromServer = fromServer;
        this.preview = preview;
    }

    // ---- The clock ----

    void tick() {
        this.age++;
        this.oldTime = this.time;
        if (this.paused) return;
        float duration = this.cutscene.duration();
        this.time = Math.min(duration, this.time + this.speed);
        float from = this.markersFired;
        this.markersFired = this.time;
        if (!this.preview) {
            List<CameraKeyframe> keyframes = this.cutscene.keyframes();
            for (int i = 0; i < keyframes.size(); i++) {
                CameraKeyframe keyframe = keyframes.get(i);
                if (keyframe.time() > this.time) break;
                int index = i;
                if (keyframe.time() > from) {
                    this.fire("keyframe", () -> CutsceneEvents.KEYFRAME.invoker().onKeyframe(this, index, keyframe));
                    this.report(CutsceneProgressPayload.keyframe(this.id, index));
                }
            }
            for (Cutscene.Subtitle subtitle : this.cutscene.subtitles()) {
                if (subtitle.start() > from && subtitle.start() <= this.time) this.fire("subtitle", () -> CutsceneEvents.SUBTITLE.invoker().onSubtitle(this, subtitle));
            }
        }
        for (Cutscene.Marker marker : this.cutscene.markers()) {
            if (marker.time() > this.time) break;
            if (marker.time() > from) {
                this.fire("marker", () -> CutsceneEvents.MARKER.invoker().onMarker(this, marker.id()));
                this.report(CutsceneProgressPayload.marker(this.id, marker.id()));
            }
        }
        // A listener may have stopped it.
        if (this.ended != null) return;
        if (!this.preview) this.fire("tick", () -> CutsceneEvents.TICK.invoker().onTick(this));
        if (this.ended != null) return;
        if (this.time >= duration) {
            // A preview waits on its last frame for the editor; a real one hands the camera back.
            if (this.preview) this.paused = true;
            else CutsceneManager.end(this, CutsceneEndReason.FINISHED);
        }
    }

    // The server that asked for this cutscene hears how far it has got.
    private void report(CutsceneProgressPayload payload) {
        if (this.fromServer && this.id != null && this.ended == null && Minecraft.getInstance().getConnection() != null) Network.sendToServer(payload);
    }

    // A listener that throws is logged, and the cutscene goes on.
    private void fire(String event, Runnable invoke) {
        try {
            invoke.run();
        } catch (RuntimeException e) {
            CoolCatCanvas.LOGGER.error("A listener of a cutscene's {} event failed", event, e);
        }
    }

    /** The time a frame shows: between the last tick's and this one's. */
    float renderTime(float partialTick) {
        return Mth.lerp(partialTick, this.oldTime, this.time);
    }

    // ---- The camera ----

    boolean drivesCamera() {
        return this.cameraEnabled;
    }

    /** Whether the editor's preview looks through the cutscene's camera, or leaves the player their own eyes. */
    public void setCameraEnabled(boolean enabled) {
        this.cameraEnabled = enabled;
    }

    /** A preview's cutscene, swapped as the editor changes it; the clock stays where it is. */
    public void setCutscene(Cutscene cutscene, Vec3 origin, float originYaw) {
        this.cutscene = cutscene;
        this.origin = origin;
        this.originYaw = originYaw;
        this.time = Math.min(this.time, cutscene.duration());
        this.oldTime = Math.min(this.oldTime, cutscene.duration());
    }

    /** The camera at a time, in the world, turned to whatever it is focused on. */
    CameraPose worldPose(float time, float playerFov, float partialTick) {
        CameraPose pose = this.cutscene.poseAt(time, playerFov).transformed(this.origin, this.originYaw);
        for (CameraFocus focus : this.cutscene.focuses()) {
            float weight = focus.weight(time);
            if (weight <= 0.0F) continue;
            Vec3 target = this.target(focus, partialTick);
            if (target != null) pose = pose.lookingAt(target, weight);
        }
        return pose;
    }

    private @Nullable Vec3 target(CameraFocus focus, float partialTick) {
        if (focus.point().isPresent()) return CameraPose.rotate(focus.point().get(), this.originYaw).add(this.origin);
        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = focus.entity().isPresent() && minecraft.level != null ? minecraft.level.getEntity(focus.entity().get()) : minecraft.player;
        // An entity the client doesn't have (too far, or gone) leaves the camera on its path.
        if (focus.entity().isPresent() && entity == null) return null;
        return entity != null ? entity.getEyePosition(partialTick) : null;
    }

    /** How much of the view is the cutscene's, against the player's own: under 1 while it blends in or out. */
    float blendWeight(float time) {
        // Scrubbing in the editor shows the path as it is, not a mix with wherever the editor's player stands.
        if (this.preview && this.paused) return 1.0F;
        float weight = 1.0F;
        float duration = this.cutscene.duration();
        if (this.cutscene.blendIn() > 0.0F && time < this.cutscene.blendIn()) weight = time / this.cutscene.blendIn();
        if (this.cutscene.blendOut() > 0.0F && time > duration - this.cutscene.blendOut()) weight = Math.min(weight, (duration - time) / this.cutscene.blendOut());
        return Easing.SINE_IN_OUT.apply(Mth.clamp(weight, 0.0F, 1.0F));
    }

    boolean locksInput() {
        return !this.preview && this.cutscene.lockInput();
    }

    // ---- CutscenePlayback ----

    @Override
    public Cutscene cutscene() {
        return this.cutscene;
    }

    @Override
    public @Nullable Identifier id() {
        return this.id;
    }

    @Override
    public float time() {
        return this.time;
    }

    @Override
    public boolean isActive() {
        return this.ended == null;
    }

    @Override
    public boolean isPaused() {
        return this.paused;
    }

    @Override
    public void pause() {
        this.setPaused(true);
    }

    @Override
    public void resume() {
        this.setPaused(false);
    }

    private void setPaused(boolean paused) {
        if (this.paused == paused) return;
        this.paused = paused;
        if (!this.preview && this.ended == null) this.fire("pause", () -> CutsceneEvents.PAUSED.invoker().onPaused(this, paused));
    }

    @Override
    public void seek(float time) {
        this.time = this.oldTime = Mth.clamp(time, 0.0F, this.cutscene.duration());
        // Markers at exactly this time still fire as play goes on from here.
        this.markersFired = Math.nextDown(this.time);
    }

    @Override
    public float speed() {
        return this.speed;
    }

    @Override
    public void setSpeed(float speed) {
        if (!(speed >= 0.0F)) throw new IllegalArgumentException("Speed can't be negative: " + speed);
        this.speed = speed;
    }

    @Override
    public void stop() {
        CutsceneManager.end(this, CutsceneEndReason.STOPPED);
    }

    @Override
    public void skip() {
        CutsceneManager.end(this, CutsceneEndReason.SKIPPED);
    }

    @Override
    public CutscenePlayback onEnd(Consumer<CutsceneEndReason> listener) {
        if (this.ended != null) listener.accept(this.ended);
        else this.endListeners.add(listener);
        return this;
    }

    @Override
    public CameraPose pose() {
        Minecraft minecraft = Minecraft.getInstance();
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        return this.worldPose(this.renderTime(partialTick), minecraft.options.fov().get(), partialTick);
    }

    /** Marks it ended and tells its own listeners; false if it already had. */
    boolean finish(CutsceneEndReason reason) {
        if (this.ended != null) return false;
        this.ended = reason;
        for (Consumer<CutsceneEndReason> listener : this.endListeners) {
            try {
                listener.accept(reason);
            } catch (RuntimeException e) {
                CoolCatCanvas.LOGGER.error("A cutscene's end listener failed", e);
            }
        }
        this.endListeners.clear();
        return true;
    }
}
