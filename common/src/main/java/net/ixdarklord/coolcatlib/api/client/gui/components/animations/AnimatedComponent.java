package net.ixdarklord.coolcatlib.api.client.gui.components.animations;

import net.ixdarklord.coolcatlib.api.client.utils.ScreenAnchor;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

public abstract class AnimatedComponent {
    public enum AnimMode {
        LOOP,
        PING_PONG,
        HOLD_LAST_FRAME
    }

    private AnimMode animMode = AnimMode.HOLD_LAST_FRAME;
    private boolean isPlaying;
    private boolean forward = true;
    private final float duration;
    private float timeline;
    private long lastStamp = System.nanoTime();

    public AnimatedComponent(float duration) {
        this.duration = Math.max(0.0001f, duration);
        this.play(false);
    }

    public void play(boolean reverse) {
        play(reverse, AnimMode.HOLD_LAST_FRAME);
    }

    public void play(boolean reverse, AnimMode mode) {
        this.isPlaying = true;
        this.animMode = mode;
        this.forward = !reverse;
        this.lastStamp = System.nanoTime();

        if (reverse && this.timeline <= 0.0f)
            this.timeline = duration;
    }

    public void update() {
        if (!isPlaying) return;

        long currentTime = System.nanoTime();
        float delta = ((currentTime - lastStamp) / 1_000_000_000f);
        lastStamp = currentTime;

        if (!Minecraft.getInstance().isPaused()) {
            timeline += forward ? delta : -delta;
        }

        if (timeline >= duration || timeline <= 0f) {
            handleEndReached();
        }

        timeline = Mth.clamp(timeline, 0f, duration);
    }

    private void handleEndReached() {
        switch (animMode) {
            case LOOP -> timeline = forward ? 0f : duration;
            case PING_PONG -> forward = !forward;
            case HOLD_LAST_FRAME -> stopAnimation();
        }
    }

    public void stopAnimation() {
        isPlaying = false;
        timeline = Mth.clamp(timeline, 0f, duration);
    }

    public abstract @NotNull Position getAlignedPosition(ScreenAnchor screenAnchor, int screenWidth, int screenHeight, int width, int height, int padding);

    public abstract @NotNull Position getRelativePosition(ScreenAnchor screenAnchor, int x, int y, int width, int height, int padding);

    public @NotNull Size getRelativeSize(int width, int height, boolean reverseValues) {
        float t = duration > 0 ? timeline / duration : 1f;
        float progress = Mth.lerp(t, reverseValues ? 1f : 0f, reverseValues ? 0f : 1f);

        int animatedWidth = Math.round(width * progress);
        int animatedHeight = Math.round(height * progress);

        return new Size(animatedWidth, animatedHeight);
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public boolean isForward() {
        return forward;
    }

    public boolean isFinished() {
        return !isPlaying && !forward;
    }

    public float getDuration() {
        return duration;
    }

    public float getTimeline() {
        return timeline;
    }

    public AnimMode getAnimationMode() {
        return animMode;
    }

    public record Position(int x, int y) {
    }

    public record Size(int width, int height) {
    }
}
