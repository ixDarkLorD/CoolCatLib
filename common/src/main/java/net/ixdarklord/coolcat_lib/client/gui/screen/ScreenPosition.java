package net.ixdarklord.coolcat_lib.client.gui.screen;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

public enum ScreenPosition implements StringRepresentable {
    TOP_LEFT("top_left", -1, -1),
    TOP("top", 0, -1),
    TOP_RIGHT("top_right", 1, -1),
    LEFT("left", -1, 0),
    CENTER("center", 0, 0),
    RIGHT("right", 1, 0),
    BOTTOM_LEFT("bottom_left", -1, 1),
    BOTTOM("bottom", 0, 1),
    BOTTOM_RIGHT("bottom_right", 1, 1);

    private final String name;
    public final int offsetX;
    public final int offsetY;

    ScreenPosition(String name, int ox, int oy) {
        this.name = name;
        this.offsetX = ox;
        this.offsetY = oy;
    }

    public int getX(int screenWidth, int width, int padding) {
        return switch (this.offsetX) {
            case -1 -> padding;
            case 1 -> screenWidth - width - padding;
            default -> (screenWidth - width) / 2;
        };
    }

    public int getY(int screenHeight, int height, int padding) {
        return switch (this.offsetY) {
            case -1 -> padding;
            case 1 -> screenHeight - height - padding;
            default -> (screenHeight - height) / 2;
        };
    }

    public ScreenPosition next() {
        int nextOrdinal = (this.ordinal() + 1) % values().length;
        return values()[nextOrdinal];
    }

    public ScreenPosition previous() {
        int previousOrdinal = (this.ordinal() - 1 + values().length) % values().length;
        return values()[previousOrdinal];
    }

    @Override
    public @NotNull String getSerializedName() {
        return this.name;
    }
}
