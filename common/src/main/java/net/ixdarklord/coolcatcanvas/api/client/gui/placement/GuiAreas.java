package net.ixdarklord.coolcatcanvas.api.client.gui.placement;

import net.ixdarklord.coolcatcanvas.internal.client.gui.GuiAreaRecorder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/**
 * What is on the screen, for placing something of your own where nothing else is (see {@link GuiPlacement}). Every
 * frame CoolCatLib records the box of each thing the HUD draws (the game's, and every mod's HUD layers: a minimap, a
 * block tooltip, the boss bars) and each thing the open screen draws in its own pass (its widgets, slots and items, and
 * what mods add there; not its background or panel art, nor the tooltip under the mouse), and the screen's widgets.
 * <p>
 * The areas are the last whole frame's, so whatever was drawn after you last frame is in them too. Draw your own
 * element inside {@link #unrecorded} so it isn't, or it would be in its own way. Client and render thread only.
 */
public final class GuiAreas {
    private GuiAreas() {
    }

    /** What the HUD drew last frame (nothing while the HUD is hidden). */
    public static List<GuiArea> hud() {
        return GuiAreaRecorder.hud();
    }

    /** What the open screen drew last frame in its own pass, and its visible widgets now (nothing with no screen open). */
    public static List<GuiArea> screen() {
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null) return List.of();
        List<GuiArea> areas = new ArrayList<>(GuiAreaRecorder.screen());
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget && widget.visible && widget.getWidth() > 0 && widget.getHeight() > 0) {
                areas.add(new GuiArea(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(), GuiArea.Kind.WIDGET));
            }
        }
        return areas;
    }

    /** Draws without recording: for your own element, which shouldn't count as being in its own way. */
    public static void unrecorded(Runnable draw) {
        GuiAreaRecorder.pause();
        try {
            draw.run();
        } finally {
            GuiAreaRecorder.resume();
        }
    }
}
