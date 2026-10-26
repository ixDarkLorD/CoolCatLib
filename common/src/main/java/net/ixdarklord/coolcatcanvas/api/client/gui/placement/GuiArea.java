package net.ixdarklord.coolcatcanvas.api.client.gui.placement;

/**
 * A box something on the screen takes up, in GUI pixels: a thing drawn ({@link Kind#DRAWN}: a sprite, a fill, a line
 * of text, an item) or a widget of the open screen ({@link Kind#WIDGET}: a button, a text box), from {@link GuiAreas}.
 */
public record GuiArea(int x, int y, int width, int height, Kind kind) {
    public enum Kind {
        DRAWN,
        WIDGET
    }

    public int right() {
        return this.x + this.width;
    }

    public int bottom() {
        return this.y + this.height;
    }

    /** Whether this and the box given overlap (touching edges don't). */
    public boolean intersects(int x, int y, int width, int height) {
        return this.x < x + width && x < this.right() && this.y < y + height && y < this.bottom();
    }

    /** Whether this lies wholly inside the box given. */
    public boolean isInside(int x, int y, int width, int height) {
        return this.x >= x && this.y >= y && this.right() <= x + width && this.bottom() <= y + height;
    }
}
