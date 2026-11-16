package net.ixdarklord.coolcatcanvas.internal.client.gui;

import net.ixdarklord.coolcatcanvas.api.client.gui.placement.GuiArea;
import net.minecraft.client.gui.navigation.ScreenRectangle;

import java.util.ArrayList;
import java.util.List;

/**
 * Records the box of everything the HUD and the open screen's own pass add to the GUI render state each frame
 * ({@code GuiRenderStateMixin}, while {@code GameRendererMixin} and {@code ScreenMixin} say which is being drawn), and
 * keeps the last whole frame's for {@code GuiAreas}. Render thread only.
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

    public static void record(ScreenRectangle bounds) {
        if (phase == Phase.NONE || paused > 0 || bounds == null || bounds.width() <= 0 || bounds.height() <= 0) return;
        GuiArea area = new GuiArea(bounds.left(), bounds.top(), bounds.width(), bounds.height(), GuiArea.Kind.DRAWN);
        (phase == Phase.HUD ? hud : screen).add(area);
    }

    /** The frame's GUI is all gathered: what it drew becomes what the areas are. */
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
