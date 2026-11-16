package net.ixdarklord.coolcatcanvas.internal.client.gui;

import net.ixdarklord.coolcatcanvas.api.client.gui.placement.GuiArea;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Records the box of everything the HUD and the open screen's own pass draw each frame ({@code GuiGraphicsMixin}, on
 * the primitives every draw ends in, while {@code GameRendererMixin} and {@code ScreenMixin} say which is being drawn),
 * and keeps the last whole frame's for {@code GuiAreas}. Render thread only.
 */
public final class GuiAreaRecorder {
    public enum Phase {
        NONE,
        HUD,
        SCREEN
    }

    private static Phase phase = Phase.NONE;
    private static int paused;
    private static List<GuiArea> hud = new ArrayList<>();
    private static List<GuiArea> screen = new ArrayList<>();
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

    /** Whether a draw now would be recorded: checked first, so the primitives skip the maths when it wouldn't. */
    public static boolean isRecording() {
        return phase != Phase.NONE && paused == 0;
    }

    public static void record(int x, int y, int width, int height) {
        if (!isRecording() || width <= 0 || height <= 0) return;
        GuiArea area = new GuiArea(x, y, width, height, GuiArea.Kind.DRAWN);
        (phase == Phase.HUD ? hud : screen).add(area);
    }

    /**
     * Records the box between two corners in the pose's space: 1.21.1 draws at once, so the box is taken to the
     * screen through the pose stack's matrix here (x and y only; the GUI's projection is flat), and its bounds kept.
     */
    public static void record(Matrix4f pose, float x1, float y1, float x2, float y2) {
        if (!isRecording()) return;
        float ax = pose.m00() * x1 + pose.m10() * y1 + pose.m30();
        float ay = pose.m01() * x1 + pose.m11() * y1 + pose.m31();
        float bx = pose.m00() * x2 + pose.m10() * y1 + pose.m30();
        float by = pose.m01() * x2 + pose.m11() * y1 + pose.m31();
        float cx = pose.m00() * x1 + pose.m10() * y2 + pose.m30();
        float cy = pose.m01() * x1 + pose.m11() * y2 + pose.m31();
        float dx = pose.m00() * x2 + pose.m10() * y2 + pose.m30();
        float dy = pose.m01() * x2 + pose.m11() * y2 + pose.m31();
        int left = (int) Math.floor(Math.min(Math.min(ax, bx), Math.min(cx, dx)));
        int top = (int) Math.floor(Math.min(Math.min(ay, by), Math.min(cy, dy)));
        int right = (int) Math.ceil(Math.max(Math.max(ax, bx), Math.max(cx, dx)));
        int bottom = (int) Math.ceil(Math.max(Math.max(ay, by), Math.max(cy, dy)));
        record(left, top, right - left, bottom - top);
    }

    /** The frame's GUI is all drawn: what it drew becomes what the areas are. */
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
