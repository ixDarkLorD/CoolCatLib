package net.ixdarklord.coolcatcanvas.internal.client.gui;

import net.ixdarklord.coolcatcanvas.api.client.gui.HudTakeover;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

// HUD takeovers: asked from GuiMixin before the HUD is drawn. An active one draws, and the HUD is skipped.
public final class HudTakeoverManager {
    private record Entry(BooleanSupplier active, HudTakeover.View view) {}

    private static final Map<Identifier, Entry> ENTRIES = new LinkedHashMap<>();

    private HudTakeoverManager() {}

    public static void register(Identifier id, BooleanSupplier active, HudTakeover.View view) {
        if (ENTRIES.putIfAbsent(id, new Entry(active, view)) != null) throw new IllegalArgumentException("HUD takeover " + id + " is already registered");
    }

    public static boolean unregister(Identifier id) {
        return ENTRIES.remove(id) != null;
    }

    /** Draws every active takeover. Returns whether there was one, in which case the HUD itself is not drawn. */
    public static boolean extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        boolean taken = false;
        for (Map.Entry<Identifier, Entry> entry : ENTRIES.entrySet()) {
            try {
                if (!entry.getValue().active().getAsBoolean()) continue;
                taken = true;
                entry.getValue().view().extract(graphics, deltaTracker);
            } catch (RuntimeException e) {
                CoolCatCanvas.LOGGER.error("HUD takeover {} failed and was removed", entry.getKey(), e);
                ENTRIES.remove(entry.getKey());
                break;
            }
        }
        return taken;
    }
}
