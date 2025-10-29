package net.ixdarklord.coolcatlib.api.client.gui.components.animations;

import net.ixdarklord.coolcatlib.api.client.utils.ScreenAnchor;
import org.jetbrains.annotations.NotNull;

public class SlideAnimation extends AnimatedComponent {
    public enum Direction {HORIZONTAL, VERTICAL}

    private final Direction direction;

    public SlideAnimation(float duration, Direction direction) {
        super(duration);
        this.direction = direction;
    }

    @Override
    public @NotNull Position getAlignedPosition(ScreenAnchor screenAnchor, int screenWidth, int screenHeight, int width, int height, int padding) {
        Size relativeSize = getRelativeSize(width + padding, height + padding, true);
        int paddingX = direction == Direction.HORIZONTAL ? (padding + relativeSize.width()) : padding;
        int paddingY = direction == Direction.VERTICAL ? (padding + relativeSize.height()) : padding;

        int xOffset = screenAnchor.getX(screenWidth, width, paddingX);
        int yOffset = screenAnchor.getY(screenHeight, height, paddingY);
        return new Position(xOffset, yOffset);
    }

    @Override
    public @NotNull Position getRelativePosition(ScreenAnchor screenAnchor, int x, int y, int width, int height, int padding) {
        Size relativeSize = getRelativeSize(width + padding, height + padding, true);
        int paddingX = direction == Direction.HORIZONTAL ? (relativeSize.width()) : 0;
        int paddingY = direction == Direction.VERTICAL ? (relativeSize.height()) : 0;

        int xOffset = screenAnchor.computeOffsetX(x, -paddingX);
        int yOffset = screenAnchor.computeOffsetY(y, -paddingY);
        return new Position(xOffset, yOffset);
    }
}