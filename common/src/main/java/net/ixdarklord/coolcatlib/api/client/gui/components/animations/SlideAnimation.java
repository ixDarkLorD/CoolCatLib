package net.ixdarklord.coolcatlib.api.client.gui.components.animations;

import net.ixdarklord.coolcatlib.api.util.ScreenPosition;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

public class SlideAnimation extends AnimatedComponent {
    public SlideAnimation(ScreenPosition screenPosition, int animationDuration) {
        super(screenPosition, animationDuration);
    }

    @Override
    public int @NotNull [] getAnimatedOffsets(int screenWidth, int screenHeight, int width, int height, int padding) {
        int xOffset;
        int yOffset;

        if (this.isPlaying()) {
            xOffset = this.getScreenPosition().getX(screenWidth, width, (int) (padding - Mth.lerp((float) this.getAnimationFrame() / this.getAnimationDuration(), width + padding, 0)));
            yOffset = this.getScreenPosition().getY(screenHeight, height, padding);
        } else {
            if (this.isEnteringScene()) {
                xOffset = this.getScreenPosition().getX(screenWidth, width, padding);
                yOffset = this.getScreenPosition().getY(screenHeight, height, padding);
            } else {
                xOffset = this.getScreenPosition().getX(screenWidth, width, padding - (width + padding));
                yOffset = this.getScreenPosition().getY(screenHeight, height, padding - (width + padding));
            }
        }
        return new int[]{xOffset, yOffset};
    }
}
