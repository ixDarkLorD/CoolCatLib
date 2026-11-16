package net.ixdarklord.coolcatcanvas.internal.client.gui;

import net.ixdarklord.coolcatcanvas.api.client.gui.HudTakeover;
import net.ixdarklord.coolcatcanvas.internal.core.CoolCatCanvas;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

// HUD takeovers: asked before the HUD is drawn (GuiMixin on Fabric, the HUD's Pre event on Forge, whose own HUD doesn't
// go through Gui#render). An active one draws, and the HUD is skipped.
public final class HudTakeoverManager {
    private record Entry(BooleanSupplier active, HudTakeover.View view) {}

    private static final Map<ResourceLocation, Entry> ENTRIES = new LinkedHashMap<>();

    private HudTakeoverManager() {}

    public static void register(ResourceLocation id, BooleanSupplier active, HudTakeover.View view) {
        if (ENTRIES.putIfAbsent(id, new Entry(active, view)) != null) throw new IllegalArgumentException("HUD takeover " + id + " is already registered");
    }

    public static boolean unregister(ResourceLocation id) {
        return ENTRIES.remove(id) != null;
    }

    /** Draws every active takeover. Returns whether there was one, in which case the HUD itself is not drawn. */
    public static boolean render(GuiGraphics graphics, float partialTick) {
        boolean taken = false;
        for (Map.Entry<ResourceLocation, Entry> entry : ENTRIES.entrySet()) {
            try {
                if (!entry.getValue().active().getAsBoolean()) continue;
                taken = true;
                entry.getValue().view().render(graphics, partialTick);
            } catch (RuntimeException e) {
                CoolCatCanvas.LOGGER.error("HUD takeover {} failed and was removed", entry.getKey(), e);
                ENTRIES.remove(entry.getKey());
                break;
            }
        }
        return taken;
    }
}
