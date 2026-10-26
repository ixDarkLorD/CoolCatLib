package net.ixdarklord.coolcatcanvas.internal.client.gui;

import net.ixdarklord.coolcatcanvas.api.client.gui.placement.GuiArea;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Records the box of everything the HUD and the open screen's own pass draw each frame, and keeps the last whole
 * frame's for {@code GuiAreas}. 1.20.1 draws at once rather than gathering a render state, so the boxes come from the
 * draw calls every GUI draw ends in ({@code GuiGraphicsMixin}, {@code FontMixin}), while {@code GameRendererMixin} and
 * {@code ScreenMixin} say which is being drawn and {@code ScreenMixin} and {@code AbstractContainerScreenMixin} pause
 * it over the screen's background. Render thread only.
 */
public final class GuiAreaRecorder {
    public enum Phase {
        NONE,
        HUD,
        SCREEN
    }

    private static Phase phase = Phase.NONE;
    private static int paused;
    private static final List<GuiArea> hud = new ArrayList<>();
    private static final List<GuiArea> screen = new ArrayList<>();
    private static List<GuiArea> lastHud = List.of();
    private static List<GuiArea> lastScreen = List.of();

    private GuiAreaRecorder() {
    }

    /** Starts recording for this part of the frame; returns the part it was, to {@link #end} with. */
    public static Phase begin(Phase now) {
        Phase was = phase;
        phase = now;
        return was;
    }

    public static void end(Phase was) {
        phase = was;
    }

    public static void pause() {
        paused++;
    }

    public static void resume() {
        if (paused > 0) paused--;
    }

    /** Whether a draw now would be recorded, so the callers can skip working out its box when it wouldn't. */
    public static boolean recording() {
        return phase != Phase.NONE && paused == 0;
    }

    /** Records a box already in screen space, in GUI pixels. */
    public static void record(int x, int y, int width, int height) {
        if (!recording() || width <= 0 || height <= 0) return;
        GuiArea area = new GuiArea(x, y, width, height, GuiArea.Kind.DRAWN);
        (phase == Phase.HUD ? hud : screen).add(area);
    }

    /**
     * Records a box drawn under a pose: its corners are taken to screen space (x and y only; the GUI's depth doesn't
     * move anything sideways) and the box round them is recorded, so a scaled or turned draw is recorded where it shows.
     */
    public static void record(Matrix4f pose, float x1, float y1, float x2, float y2) {
        if (!recording()) return;
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (int corner = 0; corner < 4; corner++) {
            float x = (corner & 1) == 0 ? x1 : x2;
            float y = (corner & 2) == 0 ? y1 : y2;
            float screenX = pose.m00() * x + pose.m10() * y + pose.m30();
            float screenY = pose.m01() * x + pose.m11() * y + pose.m31();
            minX = Math.min(minX, screenX);
            minY = Math.min(minY, screenY);
            maxX = Math.max(maxX, screenX);
            maxY = Math.max(maxY, screenY);
        }
        if (!Float.isFinite(minX) || !Float.isFinite(minY) || !Float.isFinite(maxX) || !Float.isFinite(maxY)) return;
        int left = (int) Math.floor(minX);
        int top = (int) Math.floor(minY);
        record(left, top, (int) Math.ceil(maxX) - left, (int) Math.ceil(maxY) - top);
    }

    /** The frame is all drawn: what it drew becomes what the areas are. */
    public static void endFrame() {
        lastHud = List.copyOf(hud);
        lastScreen = List.copyOf(screen);
        hud.clear();
        screen.clear();
        phase = Phase.NONE;
        paused = 0;
    }

    public static List<GuiArea> hud() {
        return lastHud;
    }

    public static List<GuiArea> screen() {
        return lastScreen;
    }
}
