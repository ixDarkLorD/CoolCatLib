package net.ixdarklord.coolcatcanvas.api.client.gui.placement;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Finds where to put something of your own so it keeps out of the way of what else is on the screen: you give its size
 * and where you'd like it, and it comes back there if that is clear, else at the nearest clear place found by sliding it
 * in the directions you allow, past whatever is in its way, without leaving the bounds you set.
 * <pre>{@code
 * GuiPlacement.Position at = GuiPlacement.hud()
 *         .size(width, 16).at(x, 4).gap(2)
 *         .directions(GuiPlacement.Direction.DOWN)
 *         .find();
 * GuiAreas.unrecorded(() -> drawMine(graphics, at.x(), at.y()));
 * }</pre>
 * By default it searches every direction within the whole screen and leaves out anything covering more than 40% of the
 * screen (vignettes and other overlays aren't in anyone's way). Client and render thread only.
 */
public final class GuiPlacement {
    private static final int MAX_STEPS = 32;

    private final List<GuiArea> areas;
    private int width;
    private int height;
    private int x;
    private int y;
    private int gap;
    private int minX;
    private int minY;
    private int maxX;
    private int maxY;
    private Direction[] directions = Direction.values();
    private float overlayShare = 0.4F;
    private Predicate<GuiArea> ignore = area -> false;

    private GuiPlacement(List<GuiArea> areas) {
        this.areas = areas;
        var window = Minecraft.getInstance().getWindow();
        this.maxX = window.getGuiScaledWidth();
        this.maxY = window.getGuiScaledHeight();
    }

    /** A placement among what the HUD drew last frame. */
    public static GuiPlacement hud() {
        return new GuiPlacement(GuiAreas.hud());
    }

    /** A placement among what the open screen drew last frame and its widgets. */
    public static GuiPlacement screen() {
        return new GuiPlacement(GuiAreas.screen());
    }

    /** A placement among the areas given. */
    public static GuiPlacement among(List<GuiArea> areas) {
        return new GuiPlacement(areas);
    }

    /** The size of what is being placed. */
    public GuiPlacement size(int width, int height) {
        this.width = width;
        this.height = height;
        return this;
    }

    /** Where it would go if nothing were in the way (its top left corner). */
    public GuiPlacement at(int x, int y) {
        this.x = x;
        this.y = y;
        return this;
    }

    /** The pixels kept clear between it and anything else. */
    public GuiPlacement gap(int gap) {
        this.gap = Math.max(0, gap);
        return this;
    }

    /** The box it must stay inside (the whole screen unless set). */
    public GuiPlacement within(int x, int y, int width, int height) {
        this.minX = x;
        this.minY = y;
        this.maxX = x + width;
        this.maxY = y + height;
        return this;
    }

    /** The directions it may slide in to get clear (every one unless set). */
    public GuiPlacement directions(Direction... directions) {
        this.directions = directions.clone();
        return this;
    }

    /** Leaves out anything covering more than this share of the screen, 0 to 1 (0.4 unless set; 1 keeps everything). */
    public GuiPlacement ignoreOverlays(float share) {
        this.overlayShare = share;
        return this;
    }

    /** Leaves out the areas this matches as well (it adds to what is left out already). */
    public GuiPlacement ignore(Predicate<GuiArea> ignore) {
        this.ignore = this.ignore.or(ignore);
        return this;
    }

    /** Leaves out the things drawn wholly inside this box, a panel of your own say (widgets there still count). */
    public GuiPlacement ignoreDrawnInside(int x, int y, int width, int height) {
        return this.ignore(area -> area.kind() == GuiArea.Kind.DRAWN && area.isInside(x, y, width, height));
    }

    /** The place found: where it goes, and whether it had to move from where it was asked to go. */
    public Position find() {
        var window = Minecraft.getInstance().getWindow();
        float overlay = window.getGuiScaledWidth() * (float) window.getGuiScaledHeight() * this.overlayShare;
        List<GuiArea> obstacles = new ArrayList<>();
        for (GuiArea area : this.areas) {
            if (this.overlayShare < 1.0F && (float) area.width() * area.height() > overlay) continue;
            if (this.ignore.test(area)) continue;
            obstacles.add(area);
        }
        if (blocking(obstacles, this.x, this.y) == null) return new Position(this.x, this.y, false);

        Position best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Direction direction : this.directions) {
            int atX = this.x;
            int atY = this.y;
            for (int step = 0; step < MAX_STEPS; step++) {
                GuiArea hit = blocking(obstacles, atX, atY);
                if (hit == null) {
                    int distance = Math.abs(atX - this.x) + Math.abs(atY - this.y);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = new Position(atX, atY, true);
                    }
                    break;
                }
                switch (direction) {
                    case UP -> atY = hit.y() - this.gap - this.height;
                    case DOWN -> atY = hit.bottom() + this.gap;
                    case LEFT -> atX = hit.x() - this.gap - this.width;
                    case RIGHT -> atX = hit.right() + this.gap;
                }
                if (!this.fits(atX, atY)) break;
            }
        }
        return best != null ? best : new Position(this.x, this.y, false);
    }

    private boolean fits(int atX, int atY) {
        return atX >= this.minX && atY >= this.minY && atX + this.width <= this.maxX && atY + this.height <= this.maxY;
    }

    private GuiArea blocking(List<GuiArea> obstacles, int atX, int atY) {
        int left = atX - this.gap;
        int top = atY - this.gap;
        int width = this.width + this.gap * 2;
        int height = this.height + this.gap * 2;
        for (GuiArea area : obstacles) {
            if (area.intersects(left, top, width, height)) return area;
        }
        return null;
    }

    public enum Direction {
        UP,
        DOWN,
        LEFT,
        RIGHT
    }

    /** Where to draw it, and whether it moved out of the way of something. */
    public record Position(int x, int y, boolean moved) {
    }
}
