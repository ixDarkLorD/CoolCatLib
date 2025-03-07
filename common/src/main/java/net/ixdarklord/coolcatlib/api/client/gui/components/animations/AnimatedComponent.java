package net.ixdarklord.coolcatlib.api.client.gui.components.animations;

import net.ixdarklord.coolcatlib.api.util.ScreenPosition;
import org.jetbrains.annotations.NotNull;

public abstract class AnimatedComponent {
    private ScreenPosition screenPosition;
    private boolean isPlaying;
    private boolean enterScene;
    private final int animationDuration;
    private int animationFrame;

    public AnimatedComponent(ScreenPosition screenPosition, int animationDuration) {
        this.screenPosition = screenPosition;
        this.animationDuration = animationDuration;
        this.startAnimation(true);
    }

    public void startAnimation(boolean enterScene) {
        this.isPlaying = true;
        this.enterScene = enterScene;
        if (!enterScene && this.animationFrame <= 0)
            this.animationFrame = this.animationDuration;
    }

    public void updateAnimation() {
        if (this.isPlaying()) {
            if (this.isEnteringScene()) {
                this.animationFrame++;
                if (this.getAnimationFrame() > this.getAnimationDuration()) {
                    this.stopAnimation();
                }
            } else {
                this.animationFrame--;
                if (this.getAnimationFrame() < 0) {
                    this.stopAnimation();
                }
            }
        }
    }

    public void stopAnimation() {
        this.isPlaying = false;
        this.animationFrame = 0;
    }

    public int @NotNull [] getAnimatedOffsets(int screenWidth, int screenHeight, int width, int height, int padding) {
        int xOffset = this.getScreenPosition().getX(screenWidth, width, padding);
        int yOffset = this.getScreenPosition().getY(screenHeight, height, padding);
        return new int[]{xOffset, yOffset};
    }

    public void setScreenPosition(ScreenPosition screenPosition) {
        this.screenPosition = screenPosition;
    }

    public boolean isPlaying() {
        return this.isPlaying;
    }

    public ScreenPosition getScreenPosition() {
        return screenPosition;
    }

    public boolean isEnteringScene() {
        return enterScene;
    }

    public int getAnimationDuration() {
        return animationDuration;
    }

    public int getAnimationFrame() {
        return animationFrame;
    }
}
